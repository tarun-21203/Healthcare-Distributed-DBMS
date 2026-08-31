package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.POJOs.SelectResponse;
import org.example.POJOs.WorkerRequest;
import org.example.POJOs.WorkerResponse;
import org.example.model.FragmentationType;
import org.example.model.TableMetadata;
import org.example.model.WorkerNode;

import java.util.*;
import java.util.stream.Collectors;

public class SelectQueryCoordinator {
    private final WorkerCommunicationService communicationService;
    private final WorkerRegistry workerRegistry;
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    private final QueryTransformer queryTransformer;
    private final ObjectMapper objectMapper;

    public SelectQueryCoordinator(WorkerCommunicationService communicationService,
                                  WorkerRegistry workerRegistry,
                                  MetadataManager metadataManager,
                                  QueryParser queryParser,
                                  QueryTransformer queryTransformer) {
        this.communicationService = communicationService;
        this.workerRegistry = workerRegistry;
        this.metadataManager = metadataManager;
        this.queryParser = queryParser;
        this.queryTransformer = queryTransformer;
        this.objectMapper = new ObjectMapper();
    }

    public void executeSelect(String query, String requestId, String currentDatabase) {
        String tableName = queryParser.extractTableName(query);
        
        if (tableName == null || currentDatabase == null) {
            System.out.println("[ERROR] Invalid SELECT query or no database selected");
            return;
        }

        TableMetadata tableMetadata = metadataManager.getMetadata()
                .getTableMetadata(currentDatabase, tableName);
        
        if (tableMetadata == null) {
            System.out.println("[ERROR] Table not found: " + currentDatabase + "." + tableName);
            return;
        }

        System.out.println("\n=== Executing SELECT query ===");
        System.out.println("Query: " + query);
        System.out.println("Request ID: " + requestId);
        System.out.println("Table: " + currentDatabase + "." + tableName);
        System.out.println("Fragmentation Type: " + tableMetadata.getFragmentationType());
        System.out.println("Replicated: " + tableMetadata.isReplicated());

        if (tableMetadata.isReplicated()) {
            handleReplicatedSelect(query, requestId, currentDatabase, tableMetadata);
        } else if (tableMetadata.getFragmentationType() == FragmentationType.HORIZONTAL) {
            handleHorizontalFragmentedSelect(query, requestId, currentDatabase, tableMetadata);
        } else if (tableMetadata.getFragmentationType() == FragmentationType.VERTICAL) {
            handleVerticalFragmentedSelect(query, requestId, currentDatabase, tableMetadata);
        } else {
            handleNonFragmentedSelect(query, requestId, currentDatabase, tableMetadata);
        }
    }

    private void handleReplicatedSelect(String query, String requestId, String currentDatabase,
                                       TableMetadata tableMetadata) {
        List<WorkerNode> activeWorkers = getActiveWorkersForTable(tableMetadata);
        
        if (activeWorkers.isEmpty()) {
            System.out.println("\n[ERROR] No active workers available for replicated table");
            return;
        }

        WorkerNode worker = activeWorkers.get(0);
        System.out.println("Querying active worker: " + worker.getWorkerId());

        SelectResponse result = queryWorker(worker, query, requestId, currentDatabase);
        
        if (result != null) {
            printTable(result);
        } else {
            System.out.println("\n[ERROR] Failed to retrieve data from worker: " + worker.getWorkerId());
        }
    }

    private void handleHorizontalFragmentedSelect(String query, String requestId, String currentDatabase,
                                                  TableMetadata tableMetadata) {
        String whereClause = queryParser.extractWhereClause(query);
        List<String> targetWorkerIds = determineHorizontalWorkers(tableMetadata, whereClause);
        
        List<WorkerNode> activeWorkers = getActiveWorkersById(targetWorkerIds);
        
        if (activeWorkers.size() < targetWorkerIds.size()) {
            List<String> unavailableWorkers = targetWorkerIds.stream()
                    .filter(id -> activeWorkers.stream().noneMatch(w -> w.getWorkerId().equals(id)))
                    .collect(Collectors.toList());
            System.out.println("\n[ERROR] Required workers are unavailable: " + unavailableWorkers);
            System.out.println("[ERROR] Cannot complete SELECT - missing data fragments");
            return;
        }

        System.out.println("Target Workers: " + targetWorkerIds);

        List<SelectResponse> responses = new ArrayList<>();
        for (WorkerNode worker : activeWorkers) {
            SelectResponse result = queryWorker(worker, query, requestId, currentDatabase);
            if (result != null) {
                responses.add(result);
            } else {
                System.out.println("\n[ERROR] Failed to retrieve data from worker: " + worker.getWorkerId());
                return;
            }
        }

        SelectResponse combined = combineHorizontalFragments(responses);
        printTable(combined);
    }

    private void handleVerticalFragmentedSelect(String query, String requestId, String currentDatabase,
                                               TableMetadata tableMetadata) {
        List<String> requiredWorkerIds = determineVerticalWorkers(query, tableMetadata);
        List<WorkerNode> activeWorkers = getActiveWorkersById(requiredWorkerIds);
        
        if (activeWorkers.size() < requiredWorkerIds.size()) {
            List<String> unavailableWorkers = requiredWorkerIds.stream()
                    .filter(id -> activeWorkers.stream().noneMatch(w -> w.getWorkerId().equals(id)))
                    .collect(Collectors.toList());
            System.out.println("\n[ERROR] Required workers are unavailable: " + unavailableWorkers);
            System.out.println("[ERROR] Cannot complete SELECT - missing column fragments");
            return;
        }

        System.out.println("Target Workers: " + requiredWorkerIds);

        Map<String, SelectResponse> workerResponses = new HashMap<>();
        for (WorkerNode worker : activeWorkers) {
            String workerQuery = queryTransformer.transformForWorker(query, "SELECT", 
                    worker.getWorkerId(), currentDatabase);
            
            if (!workerQuery.equals(query)) {
                System.out.println("[VERTICAL FRAGMENT] Query for " + worker.getWorkerId() + ": " + workerQuery);
            }
            
            SelectResponse result = queryWorker(worker, workerQuery, requestId, currentDatabase);
            if (result != null) {
                workerResponses.put(worker.getWorkerId(), result);
            } else {
                System.out.println("\n[ERROR] Failed to retrieve data from worker: " + worker.getWorkerId());
                return;
            }
        }

        SelectResponse combined = combineVerticalFragments(workerResponses, tableMetadata);
        printTable(combined);
    }

    private void handleNonFragmentedSelect(String query, String requestId, String currentDatabase,
                                          TableMetadata tableMetadata) {
        List<WorkerNode> activeWorkers = getActiveWorkersForTable(tableMetadata);
        
        if (activeWorkers.isEmpty()) {
            System.out.println("\n[ERROR] No active workers available for table");
            return;
        }

        WorkerNode worker = activeWorkers.get(0);
        System.out.println("Querying worker: " + worker.getWorkerId());

        SelectResponse result = queryWorker(worker, query, requestId, currentDatabase);
        
        if (result != null) {
            printTable(result);
        } else {
            System.out.println("\n[ERROR] Failed to retrieve data from worker: " + worker.getWorkerId());
        }
    }

    private SelectResponse queryWorker(WorkerNode worker, String query, String requestId, String database) {
        WorkerRequest request = new WorkerRequest();
        request.setRequestId(requestId);
        request.setQuery(query);
        request.setOperation("SELECT");
        request.setDatabase(database);

        WorkerResponse response = communicationService.sendToWorker(worker, request);
        
        if (response.isSuccess() && response.getData() != null) {
            try {
                return objectMapper.convertValue(response.getData(), SelectResponse.class);
            } catch (Exception e) {
                System.out.println("[ERROR] Failed to parse response from " + worker.getWorkerId() + ": " + e.getMessage());
                return null;
            }
        }
        
        return null;
    }

    private List<String> determineHorizontalWorkers(TableMetadata tableMetadata, String whereClause) {
        if (whereClause == null || tableMetadata.getFragmentRanges() == null) {
            return tableMetadata.getWorkerIds();
        }

        String fragmentColumn = tableMetadata.getFragmentationColumn();
        Map<String, String> fragmentRanges = tableMetadata.getFragmentRanges();
        
        String upperWhere = whereClause.toUpperCase();
        if (upperWhere.contains(fragmentColumn.toUpperCase())) {
            try {
                String[] parts = whereClause.split("=");
                if (parts.length == 2) {
                    String value = parts[1].trim().replace("'", "").replace(";", "");
                    
                    for (Map.Entry<String, String> entry : fragmentRanges.entrySet()) {
                        if (valueInRange(value, entry.getValue(), fragmentColumn)) {
                            return Collections.singletonList(entry.getKey());
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
        
        return tableMetadata.getWorkerIds();
    }

    private boolean valueInRange(String value, String range, String column) {
        try {
            int val = Integer.parseInt(value);
            String upperRange = range.toUpperCase();
            
            if (upperRange.contains(">=") && upperRange.contains("<=")) {
                String[] parts = upperRange.split("AND");
                if (parts.length == 2) {
                    String part1 = parts[0].trim();
                    String part2 = parts[1].trim();
                    
                    int min = Integer.parseInt(part1.split(">=")[1].trim());
                    int max = Integer.parseInt(part2.split("<=")[1].trim());
                    
                    return val >= min && val <= max;
                }
            }
        } catch (Exception e) {
        }
        return true;
    }

    private List<String> determineVerticalWorkers(String query, TableMetadata tableMetadata) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.contains("SELECT *")) {
            return tableMetadata.getWorkerIds();
        }
        
        int selectIndex = upperQuery.indexOf("SELECT");
        int fromIndex = upperQuery.indexOf("FROM");
        
        if (selectIndex == -1 || fromIndex == -1) {
            return tableMetadata.getWorkerIds();
        }
        
        String columnsStr = query.substring(selectIndex + 6, fromIndex).trim();
        List<String> requestedColumns = Arrays.stream(columnsStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        
        Map<String, List<String>> verticalFragments = tableMetadata.getVerticalFragments();
        Set<String> requiredWorkers = new HashSet<>();
        
        for (String column : requestedColumns) {
            for (Map.Entry<String, List<String>> entry : verticalFragments.entrySet()) {
                List<String> workerColumns = entry.getValue().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
                if (workerColumns.contains(column)) {
                    requiredWorkers.add(entry.getKey());
                }
            }
        }
        
        if (!requiredWorkers.isEmpty()) {
            return new ArrayList<>(requiredWorkers);
        }
        
        return tableMetadata.getWorkerIds();
    }

    private SelectResponse combineHorizontalFragments(List<SelectResponse> responses) {
        if (responses.isEmpty()) {
            return new SelectResponse(new ArrayList<>(), new ArrayList<>());
        }
        
        SelectResponse combined = new SelectResponse();
        combined.setColumns(responses.get(0).getColumns());
        
        List<Map<String, Object>> allRows = new ArrayList<>();
        for (SelectResponse response : responses) {
            allRows.addAll(response.getRows());
        }
        
        allRows.sort((row1, row2) -> {
            Object id1 = row1.get("id");
            Object id2 = row2.get("id");
            if (id1 instanceof Number && id2 instanceof Number) {
                return Integer.compare(((Number) id1).intValue(), ((Number) id2).intValue());
            }
            return 0;
        });
        
        combined.setRows(allRows);
        return combined;
    }

    private SelectResponse combineVerticalFragments(Map<String, SelectResponse> workerResponses,
                                                    TableMetadata tableMetadata) {
        if (workerResponses.isEmpty()) {
            return new SelectResponse(new ArrayList<>(), new ArrayList<>());
        }
        
        List<String> allColumns = tableMetadata.getAllColumns();
        if (allColumns == null || allColumns.isEmpty()) {
            allColumns = new ArrayList<>();
            for (SelectResponse response : workerResponses.values()) {
                for (String col : response.getColumns()) {
                    if (!allColumns.contains(col)) {
                        allColumns.add(col);
                    }
                }
            }
        }
        
        Map<Object, Map<String, Object>> rowsById = new HashMap<>();
        
        for (Map.Entry<String, SelectResponse> entry : workerResponses.entrySet()) {
            SelectResponse response = entry.getValue();
            for (Map<String, Object> row : response.getRows()) {
                Object id = row.get("id");
                if (id != null) {
                    rowsById.putIfAbsent(id, new HashMap<>());
                    rowsById.get(id).putAll(row);
                }
            }
        }
        
        List<Map<String, Object>> combinedRows = new ArrayList<>(rowsById.values());
        combinedRows.sort((row1, row2) -> {
            Object id1 = row1.get("id");
            Object id2 = row2.get("id");
            if (id1 instanceof Number && id2 instanceof Number) {
                return Integer.compare(((Number) id1).intValue(), ((Number) id2).intValue());
            }
            return 0;
        });
        
        SelectResponse combined = new SelectResponse();
        combined.setColumns(allColumns);
        combined.setRows(combinedRows);
        return combined;
    }

    private List<WorkerNode> getActiveWorkersForTable(TableMetadata tableMetadata) {
        List<String> workerIds = tableMetadata.getWorkerIds();
        return getActiveWorkersById(workerIds);
    }

    private List<WorkerNode> getActiveWorkersById(List<String> workerIds) {
        List<WorkerNode> activeWorkers = workerRegistry.getActiveWorkers();
        return activeWorkers.stream()
                .filter(w -> workerIds.contains(w.getWorkerId()))
                .collect(Collectors.toList());
    }

    private void printTable(SelectResponse selectData) {
        if (selectData.getRows().isEmpty()) {
            System.out.println("\n[No results found]");
            return;
        }
        
        System.out.println("\n+" + "-".repeat(selectData.getColumns().size() * 15 + 1) + "+");
        for (String columnName : selectData.getColumns()) {
            System.out.printf("| %-13s ", columnName);
        }
        System.out.println("|\n+" + "-".repeat(selectData.getColumns().size() * 15 + 1) + "+");
        for (Map<String, Object> data : selectData.getRows()) {
            for (String columnName : selectData.getColumns()) {
                System.out.printf("| %-13s ", data.get(columnName));
            }
            System.out.print("|\n");
        }
        System.out.println("+" + "-".repeat(selectData.getColumns().size() * 15 + 1) + "+");
    }
}
