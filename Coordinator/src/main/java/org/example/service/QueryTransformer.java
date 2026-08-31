package org.example.service;

import org.example.model.FragmentationType;
import org.example.model.TableMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTransformer {
    
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    
    public QueryTransformer(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
        this.queryParser = new QueryParser();
    }
    
    public String transformForWorker(String query, String operation, String workerId, String currentDatabase) {
        if (operation.equals("INSERT")) {
            return transformInsertForWorker(query, workerId, currentDatabase);
        } else if (operation.equals("UPDATE")) {
            return transformUpdateForWorker(query, workerId, currentDatabase);
        } else if (operation.equals("DELETE")) {
            return transformDeleteForWorker(query, workerId, currentDatabase);
        }
        
        return query;
    }
    
    private String transformInsertForWorker(String query, String workerId, String currentDatabase) {
        
        String tableName = queryParser.extractTableName(query);
        if (tableName == null || currentDatabase == null) {
            return query;
        }
        
        TableMetadata tableMeta = metadataManager.getMetadata().getTableMetadata(currentDatabase, tableName);
        if (tableMeta == null || tableMeta.getFragmentationType() != FragmentationType.VERTICAL) {
            return query;
        }
        
        Map<String, List<String>> verticalFragments = tableMeta.getVerticalFragments();
        if (verticalFragments == null || !verticalFragments.containsKey(workerId)) {
            return query;
        }
        
        List<String> workerColumns = verticalFragments.get(workerId);
        
        Pattern withColumnsPattern = Pattern.compile(
                "INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)", 
                Pattern.CASE_INSENSITIVE);
        Matcher withColumnsMatcher = withColumnsPattern.matcher(query);
        
        Pattern withoutColumnsPattern = Pattern.compile(
                "INSERT\\s+INTO\\s+(\\w+)\\s+VALUES\\s*\\(([^)]+)\\)", 
                Pattern.CASE_INSENSITIVE);
        Matcher withoutColumnsMatcher = withoutColumnsPattern.matcher(query);
        
        String[] allValues;
        List<String> originalColumns;
        
        if (withColumnsMatcher.find()) {
            String columnsStr = withColumnsMatcher.group(2);
            String valuesStr = withColumnsMatcher.group(3);
            
            originalColumns = Arrays.stream(columnsStr.split(","))
                    .map(String::trim)
                    .toList();
            allValues = valuesStr.split(",");
            
        } else if (withoutColumnsMatcher.find()) {
            String valuesStr = withoutColumnsMatcher.group(2);
            allValues = valuesStr.split(",");
            
            if (tableMeta.getAllColumns() != null && !tableMeta.getAllColumns().isEmpty()) {
                originalColumns = tableMeta.getAllColumns();
            } else {
                originalColumns = reconstructColumnOrder(verticalFragments);
            }
            
        } else {
            System.out.println("[WARNING] Could not parse INSERT query for vertical fragmentation");
            return query;
        }
        
        StringBuilder transformedQuery = new StringBuilder();
        transformedQuery.append("INSERT INTO ").append(tableName).append(" VALUES (");
        
        List<String> workerValues = extractValuesForColumns(allValues, workerColumns, originalColumns);
        
        for (int i = 0; i < workerValues.size(); i++) {
            if (i > 0) transformedQuery.append(", ");
            transformedQuery.append(workerValues.get(i).trim());
        }
        
        transformedQuery.append(");");
        
        return transformedQuery.toString();
    }
    
    private List<String> reconstructColumnOrder(Map<String, List<String>> verticalFragments) {
        List<String> allColumns = new ArrayList<>();
        
        for (List<String> fragmentColumns : verticalFragments.values()) {
            for (String col : fragmentColumns) {
                if (!allColumns.contains(col)) {
                    allColumns.add(col);
                }
            }
        }
        
        return allColumns;
    }
    
    private List<String> extractValuesForColumns(String[] allValues, 
                                                   List<String> targetColumns,
                                                   List<String> originalColumns) {
        List<String> result = new ArrayList<>();
        
        for (String targetCol : targetColumns) {
            int index = originalColumns.indexOf(targetCol);
            if (index >= 0 && index < allValues.length) {
                result.add(allValues[index]);
            } else {
                System.out.println("[WARNING] Column " + targetCol + " not found at expected position");
                result.add("NULL");
            }
        }
        
        return result;
    }
    
    private String transformUpdateForWorker(String query, String workerId, String currentDatabase) {
        String tableName = queryParser.extractTableName(query);
        if (tableName == null || currentDatabase == null) {
            return query;
        }
        
        TableMetadata tableMeta = metadataManager.getMetadata().getTableMetadata(currentDatabase, tableName);
        if (tableMeta == null || tableMeta.getFragmentationType() != FragmentationType.VERTICAL) {
            return query;
        }
        
        Map<String, List<String>> verticalFragments = tableMeta.getVerticalFragments();
        if (verticalFragments == null || !verticalFragments.containsKey(workerId)) {
            return query;
        }
        
        List<String> workerColumns = verticalFragments.get(workerId);
        
        // Extract SET clause and WHERE clause
        Pattern updatePattern = Pattern.compile(
                "UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$", 
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher updateMatcher = updatePattern.matcher(query.trim());
        
        if (!updateMatcher.find()) {
            System.out.println("[WARNING] Could not parse UPDATE query for vertical fragmentation");
            return query;
        }
        
        String setClause = updateMatcher.group(2).trim();
        String whereClause = updateMatcher.group(3);
        
        // Parse SET assignments
        String[] assignments = setClause.split(",");
        List<String> relevantAssignments = new ArrayList<>();
        
        for (String assignment : assignments) {
            String[] parts = assignment.trim().split("\\s*=\\s*", 2);
            if (parts.length == 2) {
                String columnName = parts[0].trim();
                // Check if this worker contains this column
                if (workerColumns.stream().anyMatch(col -> col.equalsIgnoreCase(columnName))) {
                    relevantAssignments.add(assignment.trim());
                }
            }
        }
        
        // If no relevant columns for this worker, return a no-op query
        if (relevantAssignments.isEmpty()) {
            return "SELECT 1; -- No columns to update on this worker";
        }
        
        // Build transformed query
        StringBuilder transformedQuery = new StringBuilder();
        transformedQuery.append("UPDATE ").append(tableName).append(" SET ");
        
        for (int i = 0; i < relevantAssignments.size(); i++) {
            if (i > 0) transformedQuery.append(", ");
            transformedQuery.append(relevantAssignments.get(i));
        }
        
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            transformedQuery.append(" WHERE ").append(whereClause);
        }
        
        transformedQuery.append(";");
        
        return transformedQuery.toString();
    }
    
    private String transformDeleteForWorker(String query, String workerId, String currentDatabase) {
        // DELETE operations don't need transformation for vertical fragmentation
        // since we're deleting entire rows, not specific columns
        // The routing logic handles which workers to send the DELETE to
        return query;
    }
}
