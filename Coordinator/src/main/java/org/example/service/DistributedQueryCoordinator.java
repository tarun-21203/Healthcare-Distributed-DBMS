package org.example.service;

import org.example.POJOs.WorkerRequest;
import org.example.POJOs.WorkerResponse;
import org.example.model.FragmentationType;
import org.example.model.WorkerNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DistributedQueryCoordinator {
    private final WorkerCommunicationService communicationService;
    private final WorkerRegistry workerRegistry;
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    private final InsertRouter insertRouter;
    private final DeleteRouter deleteRouter;
    private final UpdateRouter updateRouter;
    private final QueryTransformer queryTransformer;
    private final TransactionManager transactionManager;
    private SelectQueryCoordinator selectQueryCoordinator;
    private String currentDatabase;

    public DistributedQueryCoordinator() {
        this.communicationService = new WorkerCommunicationService();
        this.workerRegistry = WorkerRegistry.getInstance();
        this.metadataManager = MetadataManager.getInstance();
        this.queryParser = new QueryParser();
        this.insertRouter = new InsertRouter(metadataManager);
        this.deleteRouter = new DeleteRouter(metadataManager);
        this.updateRouter = new UpdateRouter(metadataManager);
        this.queryTransformer = new QueryTransformer(metadataManager);
        this.transactionManager = new TransactionManager(communicationService);
        this.communicationService.startHealthCheckScheduler();
        this.currentDatabase = null;
        
        for (WorkerNode worker : workerRegistry.getAllWorkers()) {
            metadataManager.registerWorker(worker.getWorkerId());
        }
    }

    public void executeDistributedQuery(String query) {
        // Validate query syntax first
        String validationError = queryParser.validateQuery(query);
        if (validationError != null) {
            System.err.println("\n" + validationError);
            System.err.println("Please check your SQL syntax and try again.");
            return;
        }
        
        String requestId = UUID.randomUUID().toString();
        String operation = queryParser.determineOperation(query);
        
        System.out.println("\n[COORDINATOR] Processing " + operation + " query...");
        
        if (query.trim().equalsIgnoreCase("SHOW METADATA")) {
            metadataManager.displayMetadata();
            return;
        }
        
        if (operation.equals("USE")) {
            String[] parts = query.trim().split("\\s+");
            if (parts.length >= 2) {
                String newDatabase = parts[1].replace(";", "");
                
                // Validate database name
                if (newDatabase.trim().isEmpty()) {
                    System.err.println("\n[ERROR] Database name cannot be empty");
                    return;
                }
                
                currentDatabase = newDatabase;
                communicationService.setCurrentDatabase(currentDatabase);
                System.out.println("\n[Database] Current database set to: " + currentDatabase);
            } else {
                System.err.println("\n[ERROR] USE command requires database name");
                System.err.println("Usage: USE database_name;");
                return;
            }
            // Don't return - continue to send to workers
        }
        
        if (query.toUpperCase().startsWith("REPLICATE TABLE")) {
            handleReplicateTable(query);
            return;
        }
        
        if (query.toUpperCase().startsWith("FRAGMENT TABLE")) {
            handleFragmentTable(query);
            return;
        }
        
        if (operation.equals("CREATE") && query.toUpperCase().contains("TABLE") && 
            query.toUpperCase().contains(" ON ")) {
            handleCreateTableWithWorkers(query);
            return;
        }
        
        if (operation.equals("SELECT")) {
            if (currentDatabase == null) {
                System.err.println("\n[ERROR] No database selected for SELECT operation");
                System.err.println("Use 'USE database_name;' to select a database first");
                return;
            }
            
            if (selectQueryCoordinator == null) {
                selectQueryCoordinator = new SelectQueryCoordinator(
                        communicationService, workerRegistry, metadataManager, queryParser, queryTransformer);
            }
            selectQueryCoordinator.executeSelect(query, requestId, currentDatabase);
            return;
        }
        
        // Check if database is required for this operation
        if (requiresDatabase(operation) && currentDatabase == null) {
            System.err.println("\n[ERROR] No database selected for " + operation + " operation");
            System.err.println("Use 'USE database_name;' to select a database first");
            return;
        }
        
        List<WorkerNode> targetWorkers = determineTargetWorkers(query, operation);
        
        if (targetWorkers.isEmpty()) {
            System.err.println("\n[ERROR] No active workers available for this operation!");
            
            // Provide specific guidance based on operation
            if (operation.equals("INSERT") || operation.equals("SELECT")) {
                String tableName = queryParser.extractTableName(query);
                if (tableName != null && currentDatabase != null) {
                    System.err.println("Table '" + tableName + "' may not exist or workers containing this table are offline.");
                    System.err.println("Use 'SHOW METADATA' to see available tables and their worker assignments.");
                } else {
                    System.err.println("Please ensure workers are running and tables are properly configured.");
                }
            } else {
                System.err.println("Please ensure at least one worker is active and accessible.");
            }
            
            showWorkerStatus();
            return;
        }

        System.out.println("\n=== Executing query on " + targetWorkers.size() + " worker(s) ===");
        System.out.println("Query: " + query);
        System.out.println("Request ID: " + requestId);
        System.out.println("Target Workers: " + targetWorkers.stream()
                .map(WorkerNode::getWorkerId).toList());

        List<WorkerResponse> responses = new ArrayList<>();
        List<WorkerNode> successfulWorkers = new ArrayList<>();
        
        for (WorkerNode worker : targetWorkers) {
            String workerQuery = queryTransformer.transformForWorker(query, operation, worker.getWorkerId(), currentDatabase);
            
            WorkerRequest request = new WorkerRequest();
            request.setRequestId(requestId);
            request.setQuery(workerQuery);
            request.setOperation(operation);
            request.setDatabase(currentDatabase);
            
            if (!workerQuery.equals(query)) {
                System.out.println("[VERTICAL FRAGMENT] Query for " + worker.getWorkerId() + ": " + workerQuery);
            }
            
            WorkerResponse response = communicationService.sendToWorker(worker, request);
            responses.add(response);
            
            if (response.isSuccess()) {
                successfulWorkers.add(worker);
            }
        }

        System.out.println("\n=== Execution Results ===");
        
        int successCount = 0;
        int failureCount = 0;
        List<String> errorMessages = new ArrayList<>();
        
        for (WorkerResponse response : responses) {
            if (response.isSuccess()) {
                System.out.println("Worker " + response.getWorkerId() + ": SUCCESS");
                if (response.getMessage() != null && !response.getMessage().isEmpty()) {
                    System.out.println("   " + response.getMessage());
                }
                if (response.getData() != null) {
                    System.out.println("   Data: " + response.getData());
                }
                successCount++;
            } else {
                System.err.println("Worker " + response.getWorkerId() + ": FAILED");
                String errorMsg = response.getMessage() != null ? response.getMessage() : "Unknown error";
                System.err.println("   Error: " + errorMsg);
                errorMessages.add("Worker " + response.getWorkerId() + ": " + errorMsg);
                failureCount++;
            }
        }
        
        // Summary
        System.out.println("\n=== Summary ===");
        System.out.println("Successful: " + successCount + "/" + responses.size() + " workers");
        if (failureCount > 0) {
            System.err.println("Failed: " + failureCount + "/" + responses.size() + " workers");
        }
        
        boolean allSuccess = responses.stream().allMatch(WorkerResponse::isSuccess);
        
        if (!allSuccess && !successfulWorkers.isEmpty()) {
            System.err.println("\n[TRANSACTION] Partial failure detected - initiating rollback");
            System.err.println("Rolling back changes on successful workers to maintain consistency...");
            transactionManager.rollback(query, operation, successfulWorkers, requestId, currentDatabase);
        } else if (allSuccess) {
            System.out.println("\n[TRANSACTION] All workers succeeded - transaction committed");
            updateMetadataAfterQuery(query, operation, targetWorkers);
        } else {
            System.err.println("\n[TRANSACTION] All workers failed - no rollback needed");
            
            // Provide helpful error analysis
            if (!errorMessages.isEmpty()) {
                System.err.println("\nError Analysis:");
                for (String error : errorMessages) {
                    System.err.println("   - " + error);
                }
                
                // Suggest common solutions
                System.err.println("\nPossible Solutions:");
                if (errorMessages.stream().anyMatch(msg -> msg.toLowerCase().contains("authentication"))) {
                    System.err.println("   - Check worker authentication credentials");
                    System.err.println("   - Ensure workers are properly configured");
                }
                if (errorMessages.stream().anyMatch(msg -> msg.toLowerCase().contains("table") || msg.toLowerCase().contains("database"))) {
                    System.err.println("   - Verify table/database exists on target workers");
                    System.err.println("   - Use 'SHOW METADATA' to check table distribution");
                }
                if (errorMessages.stream().anyMatch(msg -> msg.toLowerCase().contains("connection") || msg.toLowerCase().contains("timeout"))) {
                    System.err.println("   - Check worker connectivity and status");
                    System.err.println("   - Verify workers are running and accessible");
                }
                if (errorMessages.stream().anyMatch(msg -> msg.toLowerCase().contains("syntax") || msg.toLowerCase().contains("invalid"))) {
                    System.err.println("   - Review SQL syntax for worker-specific requirements");
                    System.err.println("   - Check column names and data types");
                }
            }
        }
    }
    
    private List<WorkerNode> determineTargetWorkers(String query, String operation) {
        List<WorkerNode> activeWorkers = workerRegistry.getActiveWorkers();
        List<String> activeWorkerIds = activeWorkers.stream()
                .map(WorkerNode::getWorkerId)
                .toList();
        
        if (operation.equals("INSERT")) {
            String tableName = queryParser.extractTableName(query);
            if (tableName != null && currentDatabase != null) {
                List<String> targetWorkerIds = insertRouter.determineTargetWorkers(query, tableName, currentDatabase);
                
                if (!targetWorkerIds.isEmpty()) {
                    // Check if all required workers are available
                    List<String> unavailableWorkers = targetWorkerIds.stream()
                            .filter(id -> !activeWorkerIds.contains(id))
                            .toList();
                    
                    if (!unavailableWorkers.isEmpty()) {
                        System.out.println("[ERROR] Required workers are not available: " + unavailableWorkers);
                        System.out.println("[ERROR] Cannot proceed with INSERT - all workers must be available");
                        return new ArrayList<>();
                    }
                    
                    return activeWorkers.stream()
                            .filter(w -> targetWorkerIds.contains(w.getWorkerId()))
                            .toList();
                }
            }
        }
        
        if (operation.equals("DELETE")) {
            String tableName = queryParser.extractTableName(query);
            if (tableName != null && currentDatabase != null) {
                List<String> targetWorkerIds = deleteRouter.determineTargetWorkers(query, tableName, currentDatabase);
                
                if (!targetWorkerIds.isEmpty()) {
                    // Check if all required workers are available
                    List<String> unavailableWorkers = targetWorkerIds.stream()
                            .filter(id -> !activeWorkerIds.contains(id))
                            .toList();
                    
                    if (!unavailableWorkers.isEmpty()) {
                        System.out.println("[ERROR] Required workers are not available: " + unavailableWorkers);
                        System.out.println("[ERROR] Cannot proceed with DELETE - all workers must be available");
                        return new ArrayList<>();
                    }
                    
                    return activeWorkers.stream()
                            .filter(w -> targetWorkerIds.contains(w.getWorkerId()))
                            .toList();
                }
            }
        }
        
        if (operation.equals("UPDATE")) {
            String tableName = queryParser.extractTableName(query);
            if (tableName != null && currentDatabase != null) {
                List<String> targetWorkerIds = updateRouter.determineTargetWorkers(query, tableName, currentDatabase);
                
                if (!targetWorkerIds.isEmpty()) {
                    // Check if all required workers are available
                    List<String> unavailableWorkers = targetWorkerIds.stream()
                            .filter(id -> !activeWorkerIds.contains(id))
                            .toList();
                    
                    if (!unavailableWorkers.isEmpty()) {
                        System.out.println("[ERROR] Required workers are not available: " + unavailableWorkers);
                        System.out.println("[ERROR] Cannot proceed with UPDATE - all workers must be available");
                        return new ArrayList<>();
                    }
                    
                    return activeWorkers.stream()
                            .filter(w -> targetWorkerIds.contains(w.getWorkerId()))
                            .toList();
                }
            }
        }
        
        if (operation.equals("SELECT")) {
            String tableName = queryParser.extractTableName(query);
            if (tableName != null && currentDatabase != null) {
                List<String> workerIds = metadataManager.getWorkersForQuery(
                        currentDatabase, tableName, queryParser.extractWhereClause(query));
                
                if (!workerIds.isEmpty()) {
                    return activeWorkers.stream()
                            .filter(w -> workerIds.contains(w.getWorkerId()))
                            .toList();
                }
            }
        }
        
        return activeWorkers;
    }
    
    private void updateMetadataAfterQuery(String query, String operation, List<WorkerNode> workers) {
        if (operation.equals("CREATE") && query.toUpperCase().contains("TABLE")) {
            System.out.println("\n[Error] CREATE TABLE requires worker specification");
            System.out.println("Usage: CREATE TABLE tablename (columns) ON worker1, worker2;");
            return;
        }
        
        if (operation.equals("DROP") && query.toUpperCase().contains("TABLE")) {
            String tableName = queryParser.extractTableName(query);
            if (tableName != null && currentDatabase != null) {
                metadataManager.removeTable(currentDatabase, tableName);
                System.out.println("\n[Metadata] Removed table: " + currentDatabase + "." + tableName);
            }
        }
    }

    public void showWorkerStatus() {
        List<WorkerNode> workers = workerRegistry.getAllWorkers();
        System.out.println("\n=== Worker Status ===");
        if (workers.isEmpty()) {
            System.out.println("No workers registered.");
        } else {
            for (WorkerNode worker : workers) {
                System.out.println("\nWorker ID: " + worker.getWorkerId());
                System.out.println("Address: " + worker.getBaseUrl());
                System.out.println("Status: " + (worker.isActive() ? "ACTIVE" : "INACTIVE"));
                System.out.println("Last Heartbeat: " + worker.getLastHeartbeat());
            }
        }
    }

    private boolean requiresDatabase(String operation) {
        return operation.equals("INSERT") || operation.equals("UPDATE") || 
               operation.equals("DELETE") || operation.equals("DROP");
    }

    public void shutdown() {
        communicationService.shutdown();
    }
    
    private void handleCreateTableWithWorkers(String query) {
        String upperQuery = query.toUpperCase();
        boolean isHorizontal = upperQuery.contains(" HORIZONTAL ");
        boolean isVertical = upperQuery.contains(" VERTICAL ");
        
        Pattern pattern;
        if (isHorizontal) {
            pattern = Pattern.compile(
                    "CREATE\\s+TABLE\\s+(\\w+)\\s*\\(([^)]+)\\)\\s+ON\\s+([^H]+)\\s+HORIZONTAL\\s+(\\w+)\\s+RANGE\\s+(.+);?",
                    Pattern.CASE_INSENSITIVE);
        } else if (isVertical) {
            pattern = Pattern.compile(
                    "CREATE\\s+TABLE\\s+(\\w+)\\s*\\(([^)]+)\\)\\s+ON\\s+([^V]+)\\s+VERTICAL\\s+(.+);?",
                    Pattern.CASE_INSENSITIVE);
        } else {
            pattern = Pattern.compile(
                    "CREATE\\s+TABLE\\s+(\\w+)\\s*\\(([^)]+)\\)\\s+ON\\s+(.+);?",
                    Pattern.CASE_INSENSITIVE);
        }
        
        Matcher matcher = pattern.matcher(query);
        
        if (!matcher.find()) {
            System.out.println("Error: Invalid CREATE TABLE syntax");
            return;
        }
        
        String tableName = matcher.group(1);
        String columns = matcher.group(2);
        String workersStr = matcher.group(3).replace(";", "").trim();
        
        List<String> workerIds = Arrays.stream(workersStr.split(","))
                .map(String::trim)
                .toList();
        
        if (currentDatabase == null) {
            System.out.println("Error: No database selected. Use 'USE database;' first.");
            return;
        }
        
        List<WorkerNode> availableWorkers = workerRegistry.getActiveWorkers();
        List<String> availableWorkerIds = availableWorkers.stream()
                .map(WorkerNode::getWorkerId)
                .toList();
        
        List<WorkerNode> targetWorkers = new ArrayList<>();
        for (String workerId : workerIds) {
            if (!availableWorkerIds.contains(workerId)) {
                System.out.println("Error: Worker '" + workerId + "' not found or inactive");
                return;
            }
            WorkerNode worker = availableWorkers.stream()
                    .filter(w -> w.getWorkerId().equals(workerId))
                    .findFirst()
                    .orElse(null);
            if (worker != null) {
                targetWorkers.add(worker);
            }
        }
        
        Map<String, List<String>> verticalFragments = new HashMap<>();
        if (isVertical) {
            String columnsStr = matcher.group(4).replace(";", "").trim();
            verticalFragments = parseVerticalFragments(columnsStr);
        }
        
        String requestId = UUID.randomUUID().toString();
        
        System.out.println("\n=== Creating table on " + targetWorkers.size() + " worker(s) ===");
        System.out.println("Table: " + tableName);
        System.out.println("Target Workers: " + workerIds);
        
        List<WorkerResponse> responses = new ArrayList<>();
        for (WorkerNode worker : targetWorkers) {
            String createQuery;
            
            if (isVertical && verticalFragments.containsKey(worker.getWorkerId())) {
                List<String> workerColumns = verticalFragments.get(worker.getWorkerId());
                Map<String, String> columnTypes = parseColumnDefinitions(columns);
                
                StringBuilder workerColumnsStr = new StringBuilder();
                for (int i = 0; i < workerColumns.size(); i++) {
                    if (i > 0) workerColumnsStr.append(", ");
                    String colName = workerColumns.get(i);
                    String colType = columnTypes.get(colName.toLowerCase());
                    if (colType != null) {
                        workerColumnsStr.append(colName).append(" ").append(colType);
                    } else {
                        workerColumnsStr.append(colName).append(" STRING");
                    }
                }
                
                createQuery = "CREATE TABLE " + tableName + " (" + workerColumnsStr + ");";
                System.out.println("[VERTICAL FRAGMENT] Query for " + worker.getWorkerId() + ": " + createQuery);
            } else {
                createQuery = "CREATE TABLE " + tableName + " (" + columns + ");";
            }
            
            WorkerRequest request = new WorkerRequest();
            request.setRequestId(requestId);
            request.setQuery(createQuery);
            request.setOperation("CREATE");
            request.setDatabase(currentDatabase);
            
            WorkerResponse response = communicationService.sendToWorker(worker, request);
            responses.add(response);
        }
        
        System.out.println("\n=== Results ===");
        boolean allSuccess = true;
        for (WorkerResponse response : responses) {
            System.out.println("\nWorker: " + response.getWorkerId());
            System.out.println("Success: " + response.isSuccess());
            System.out.println("Message: " + response.getMessage());
            if (!response.isSuccess()) {
                allSuccess = false;
            }
        }
        
        if (allSuccess) {
            if (isHorizontal) {
                String fragmentColumn = matcher.group(4);
                String rangesStr = matcher.group(5).replace(";", "").trim();
                
                Map<String, String> fragmentRanges = new HashMap<>();
                String[] rangeParts = rangesStr.split(",");
                for (String part : rangeParts) {
                    String[] workerRange = part.trim().split(":", 2);
                    if (workerRange.length == 2) {
                        String workerId = workerRange[0].trim();
                        String range = workerRange[1].trim();
                        fragmentRanges.put(workerId, range);
                    }
                }
                
                metadataManager.registerTable(currentDatabase, tableName, workerIds,
                        FragmentationType.HORIZONTAL, false);
                metadataManager.registerHorizontalFragmentation(currentDatabase, tableName,
                        fragmentColumn, fragmentRanges);
                
                System.out.println("\n[Metadata] Registered table: " + currentDatabase + "." + tableName);
                System.out.println("[Metadata] Workers: " + workerIds);
                System.out.println("[Metadata] Fragmentation: HORIZONTAL on column '" + fragmentColumn + "'");
                System.out.println("[Metadata] Ranges: " + fragmentRanges);
                
            } else if (isVertical) {
                List<String> allColumns = queryParser.extractColumnNames(columns);
                
                metadataManager.registerTable(currentDatabase, tableName, workerIds,
                        FragmentationType.VERTICAL, false);
                metadataManager.registerVerticalFragmentation(currentDatabase, tableName,
                        verticalFragments, allColumns);
                
                System.out.println("\n[Metadata] Registered table: " + currentDatabase + "." + tableName);
                System.out.println("[Metadata] Workers: " + workerIds);
                System.out.println("[Metadata] Column Order: " + allColumns);
                System.out.println("[Metadata] Fragmentation: VERTICAL");
                System.out.println("[Metadata] Fragments: " + verticalFragments);
                
            } else {
                metadataManager.registerTable(currentDatabase, tableName, workerIds,
                        FragmentationType.NONE, false);
                
                System.out.println("\n[Metadata] Registered table: " + currentDatabase + "." + tableName);
                System.out.println("[Metadata] Workers: " + workerIds);
                System.out.println("[Metadata] Status: NOT REPLICATED");
            }
        }
    }
    
    private void handleReplicateTable(String query) {
        Pattern pattern = Pattern.compile("REPLICATE\\s+TABLE\\s+(\\w+)\\s+ON\\s+(.+);?", 
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        
        if (!matcher.find()) {
            System.out.println("Error: Invalid REPLICATE TABLE syntax");
            return;
        }
        
        String tableName = matcher.group(1);
        String workersStr = matcher.group(2).replace(";", "").trim();
        List<String> workerIds = Arrays.stream(workersStr.split(","))
                .map(String::trim)
                .toList();
        
        if (currentDatabase == null) {
            System.out.println("Error: No database selected.");
            return;
        }
        
        metadataManager.registerTable(currentDatabase, tableName, workerIds, 
                FragmentationType.NONE, true);
        
        System.out.println("\n[Metadata] Table replication configured:");
        System.out.println("  Table: " + currentDatabase + "." + tableName);
        System.out.println("  Workers: " + workerIds);
    }
    
    private void handleFragmentTable(String query) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.contains("HORIZONTAL")) {
            handleHorizontalFragmentation(query);
        } else if (upperQuery.contains("VERTICAL")) {
            handleVerticalFragmentation(query);
        }
    }
    
    private void handleHorizontalFragmentation(String query) {
        Pattern pattern = Pattern.compile(
                "FRAGMENT\\s+TABLE\\s+(\\w+)\\s+HORIZONTAL\\s+ON\\s+(\\w+)\\s+RANGE\\s+(.+);?",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        
        if (!matcher.find()) {
            System.out.println("Error: Invalid HORIZONTAL fragmentation syntax");
            return;
        }
        
        String tableName = matcher.group(1);
        String column = matcher.group(2);
        String rangesStr = matcher.group(3).replace(";", "").trim();
        
        if (currentDatabase == null) {
            System.out.println("Error: No database selected.");
            return;
        }
        
        Map<String, String> fragmentRanges = new HashMap<>();
        List<String> workerIds = new ArrayList<>();
        
        String[] rangeParts = rangesStr.split(",");
        for (String part : rangeParts) {
            String[] workerRange = part.trim().split(":", 2);
            if (workerRange.length == 2) {
                String workerId = workerRange[0].trim();
                String range = workerRange[1].trim();
                fragmentRanges.put(workerId, range);
                workerIds.add(workerId);
            }
        }
        
        metadataManager.registerTable(currentDatabase, tableName, workerIds,
                FragmentationType.HORIZONTAL, false);
        metadataManager.registerHorizontalFragmentation(currentDatabase, tableName,
                column, fragmentRanges);
        
        System.out.println("\n[Metadata] Horizontal fragmentation configured:");
        System.out.println("  Table: " + currentDatabase + "." + tableName);
    }
    
    private void handleVerticalFragmentation(String query) {
        Pattern pattern = Pattern.compile(
                "FRAGMENT\\s+TABLE\\s+(\\w+)\\s+VERTICAL\\s+COLUMNS\\s+(.+);?",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        
        if (!matcher.find()) {
            System.out.println("Error: Invalid VERTICAL fragmentation syntax");
            return;
        }
        
        String tableName = matcher.group(1);
        String columnsStr = matcher.group(2).replace(";", "").trim();
        
        if (currentDatabase == null) {
            System.out.println("Error: No database selected.");
            return;
        }
        
        Map<String, List<String>> verticalFragments = parseVerticalFragments(columnsStr);
        List<String> workerIds = new ArrayList<>(verticalFragments.keySet());
        
        metadataManager.registerTable(currentDatabase, tableName, workerIds,
                FragmentationType.VERTICAL, false);
        metadataManager.registerVerticalFragmentation(currentDatabase, tableName,
                verticalFragments);
        
        System.out.println("\n[Metadata] Vertical fragmentation configured:");
        System.out.println("  Table: " + currentDatabase + "." + tableName);
    }
    
    private Map<String, List<String>> parseVerticalFragments(String columnsStr) {
        Map<String, List<String>> verticalFragments = new HashMap<>();
        
        Pattern workerPattern = Pattern.compile("(\\S+)\\s*:");
        Matcher workerMatcher = workerPattern.matcher(columnsStr);
        
        List<Integer> positions = new ArrayList<>();
        List<String> workers = new ArrayList<>();
        
        while (workerMatcher.find()) {
            positions.add(workerMatcher.end());
            workers.add(workerMatcher.group(1).trim());
        }
        
        for (int i = 0; i < workers.size(); i++) {
            String workerId = workers.get(i);
            int startPos = positions.get(i);
            int endPos = (i < workers.size() - 1) ? 
                         columnsStr.indexOf(workers.get(i + 1) + ":", startPos) : 
                         columnsStr.length();
            
            String workerCols = columnsStr.substring(startPos, endPos).trim();
            
            if (workerCols.endsWith(",")) {
                workerCols = workerCols.substring(0, workerCols.length() - 1).trim();
            }
            
            List<String> columnsList = Arrays.stream(workerCols.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.contains(":"))
                    .toList();
            
            verticalFragments.put(workerId, new ArrayList<>(columnsList));
        }
        
        return verticalFragments;
    }
    
    private Map<String, String> parseColumnDefinitions(String columnsStr) {
        Map<String, String> columnTypes = new HashMap<>();
        String[] columnDefs = columnsStr.split(",");
        
        for (String columnDef : columnDefs) {
            String[] parts = columnDef.trim().split("\\s+");
            if (parts.length >= 2) {
                String columnName = parts[0].toLowerCase();
                String columnType = parts[1];
                columnTypes.put(columnName, columnType);
            }
        }
        
        return columnTypes;
    }
}
