package org.example.service;

import org.example.model.FragmentationType;
import org.example.model.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateRouter {
    
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    
    public UpdateRouter(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
        this.queryParser = new QueryParser();
    }
    
    public List<String> determineTargetWorkers(String query, String tableName, String currentDatabase) {
        TableMetadata tableMeta = metadataManager.getMetadata().getTableMetadata(currentDatabase, tableName);
        
        if (tableMeta == null) {
            System.out.println("[Warning] No metadata found for table: " + currentDatabase + "." + tableName);
            return new ArrayList<>();
        }
        
        System.out.println("[DEBUG] UPDATE - Table: " + tableName);
        System.out.println("[DEBUG] UPDATE - Fragmentation Type: " + tableMeta.getFragmentationType());
        System.out.println("[DEBUG] UPDATE - Is Replicated: " + tableMeta.isReplicated());
        System.out.println("[DEBUG] UPDATE - Worker IDs: " + tableMeta.getWorkerIds());
        
        // For UPDATE operations, we need to consider WHERE clause
        String whereClause = queryParser.extractWhereClause(query);
        
        if (tableMeta.isReplicated()) {
            System.out.println("[UPDATE Routing] Table is REPLICATED - updating on all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.HORIZONTAL) {
            List<String> targetWorkers = determineHorizontalFragmentWorkers(query, tableMeta, whereClause);
            if (!targetWorkers.isEmpty()) {
                System.out.println("[UPDATE Routing] HORIZONTAL fragmentation - updating on workers: " + targetWorkers);
                return targetWorkers;
            } else {
                System.out.println("[Warning] Could not determine target workers for horizontal fragmentation - using all workers");
                return tableMeta.getWorkerIds();
            }
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.VERTICAL) {
            List<String> targetWorkers = determineVerticalFragmentWorkers(query, tableMeta);
            if (!targetWorkers.isEmpty()) {
                System.out.println("[UPDATE Routing] VERTICAL fragmentation - updating on workers: " + targetWorkers);
                return targetWorkers;
            } else {
                System.out.println("[UPDATE Routing] VERTICAL fragmentation - updating on all workers: " + tableMeta.getWorkerIds());
                return tableMeta.getWorkerIds();
            }
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.NONE && !tableMeta.isReplicated()) {
            System.out.println("[UPDATE Routing] No distribution strategy - updating on all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        return tableMeta.getWorkerIds();
    }
    
    private List<String> determineHorizontalFragmentWorkers(String query, TableMetadata tableMeta, String whereClause) {
        Map<String, String> fragmentRanges = tableMeta.getFragmentRanges();
        String fragmentColumn = tableMeta.getFragmentationColumn();
        
        if (fragmentRanges == null || fragmentColumn == null) {
            return tableMeta.getWorkerIds();
        }
        
        // If no WHERE clause, we need to check all workers
        if (whereClause == null || whereClause.trim().isEmpty()) {
            System.out.println("[UPDATE Routing] No WHERE clause - updating on all workers for safety");
            return tableMeta.getWorkerIds();
        }
        
        // Try to extract specific value from WHERE clause
        String fragmentValue = extractFragmentValueFromWhere(whereClause, fragmentColumn);
        
        if (fragmentValue != null) {
            // We have a specific value, find the exact worker
            System.out.println("[DEBUG] UPDATE - Fragment column '" + fragmentColumn + "' value: " + fragmentValue);
            
            for (Map.Entry<String, String> entry : fragmentRanges.entrySet()) {
                String workerId = entry.getKey();
                String condition = entry.getValue();
                
                if (evaluateFragmentConditionString(fragmentValue, condition, fragmentColumn)) {
                    System.out.println("[DEBUG] UPDATE - Value '" + fragmentValue + "' matches worker " + workerId + " condition: " + condition);
                    return List.of(workerId);
                }
            }
        }
        
        // If we can't determine specific worker, check all workers for safety
        System.out.println("[UPDATE Routing] Cannot determine specific worker - using all workers for safety");
        return tableMeta.getWorkerIds();
    }
    
    private List<String> determineVerticalFragmentWorkers(String query, TableMetadata tableMeta) {
        // For vertical fragmentation, we need to determine which workers contain the columns being updated
        Map<String, List<String>> verticalFragments = tableMeta.getVerticalFragments();
        
        if (verticalFragments == null || verticalFragments.isEmpty()) {
            return tableMeta.getWorkerIds();
        }
        
        // Extract columns being updated from SET clause
        List<String> updatedColumns = extractUpdatedColumns(query);
        
        if (updatedColumns.isEmpty()) {
            System.out.println("[UPDATE Routing] Could not extract updated columns - using all workers");
            return tableMeta.getWorkerIds();
        }
        
        List<String> targetWorkers = new ArrayList<>();
        
        // Find workers that contain any of the updated columns
        for (Map.Entry<String, List<String>> entry : verticalFragments.entrySet()) {
            String workerId = entry.getKey();
            List<String> workerColumns = entry.getValue();
            
            // Check if this worker contains any of the updated columns
            for (String updatedColumn : updatedColumns) {
                if (workerColumns.stream().anyMatch(col -> col.equalsIgnoreCase(updatedColumn))) {
                    if (!targetWorkers.contains(workerId)) {
                        targetWorkers.add(workerId);
                    }
                    break;
                }
            }
        }
        
        System.out.println("[DEBUG] UPDATE - Updated columns: " + updatedColumns);
        System.out.println("[DEBUG] UPDATE - Target workers for vertical fragments: " + targetWorkers);
        
        return targetWorkers;
    }
    
    private List<String> extractUpdatedColumns(String query) {
        List<String> columns = new ArrayList<>();
        
        // Find SET clause
        Pattern setPattern = Pattern.compile("SET\\s+(.+?)(?:\\s+WHERE|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher setMatcher = setPattern.matcher(query);
        
        if (setMatcher.find()) {
            String setClause = setMatcher.group(1).trim();
            
            // Split by comma and extract column names
            String[] assignments = setClause.split(",");
            for (String assignment : assignments) {
                String[] parts = assignment.trim().split("\\s*=\\s*");
                if (parts.length >= 1) {
                    columns.add(parts[0].trim());
                }
            }
        }
        
        return columns;
    }
    
    private String extractFragmentValueFromWhere(String whereClause, String fragmentColumn) {
        // Look for patterns like "id = 5", "Apt_type = 'doctor'", or "order_id = 10"
        Pattern pattern = Pattern.compile(fragmentColumn + "\\s*=\\s*['\"]?([^'\"\\s,]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(whereClause);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    private boolean evaluateFragmentCondition(int value, String condition, String columnName) {
        condition = condition.trim();
        
        if (condition.toUpperCase().contains(" AND ")) {
            String[] parts = condition.split("(?i)\\s+AND\\s+");
            for (String part : parts) {
                if (!evaluateSingleCondition(value, part.trim(), columnName)) {
                    return false;
                }
            }
            return true;
        }
        
        return evaluateSingleCondition(value, condition, columnName);
    }
    
    private boolean evaluateSingleCondition(int value, String condition, String columnName) {
        condition = condition.trim();
        condition = condition.replaceAll("(?i)" + columnName + "\\s*", "");
        
        if (condition.startsWith(">=")) {
            int threshold = Integer.parseInt(condition.substring(2).trim());
            return value >= threshold;
        } else if (condition.startsWith("<=")) {
            int threshold = Integer.parseInt(condition.substring(2).trim());
            return value <= threshold;
        } else if (condition.startsWith(">")) {
            int threshold = Integer.parseInt(condition.substring(1).trim());
            return value > threshold;
        } else if (condition.startsWith("<")) {
            int threshold = Integer.parseInt(condition.substring(1).trim());
            return value < threshold;
        } else if (condition.startsWith("=")) {
            int threshold = Integer.parseInt(condition.substring(1).trim());
            return value == threshold;
        }
        
        return false;
    }
    
    private boolean evaluateFragmentConditionString(String value, String condition, String columnName) {
        condition = condition.trim();
        
        // Handle string equality conditions like "Apt_type = 'doctor'"
        Pattern equalityPattern = Pattern.compile(columnName + "\\s*=\\s*['\"]?([^'\"\\s]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = equalityPattern.matcher(condition);
        
        if (matcher.find()) {
            String expectedValue = matcher.group(1);
            boolean matches = value.equalsIgnoreCase(expectedValue);
            System.out.println("[DEBUG] UPDATE - Comparing '" + value + "' with '" + expectedValue + "': " + matches);
            return matches;
        }
        
        // Fallback to numeric evaluation for backward compatibility
        try {
            int numericValue = Integer.parseInt(value);
            return evaluateFragmentCondition(numericValue, condition, columnName);
        } catch (NumberFormatException e) {
            System.out.println("[Warning] Could not evaluate UPDATE condition for string value: " + value + " with condition: " + condition);
            return false;
        }
    }
}