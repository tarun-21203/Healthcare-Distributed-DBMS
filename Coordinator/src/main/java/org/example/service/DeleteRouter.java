package org.example.service;

import org.example.model.FragmentationType;
import org.example.model.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeleteRouter {
    
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    
    public DeleteRouter(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
        this.queryParser = new QueryParser();
    }
    
    public List<String> determineTargetWorkers(String query, String tableName, String currentDatabase) {
        TableMetadata tableMeta = metadataManager.getMetadata().getTableMetadata(currentDatabase, tableName);
        
        if (tableMeta == null) {
            System.out.println("[Warning] No metadata found for table: " + currentDatabase + "." + tableName);
            return new ArrayList<>();
        }
        
        System.out.println("[DEBUG] DELETE - Table: " + tableName);
        System.out.println("[DEBUG] DELETE - Fragmentation Type: " + tableMeta.getFragmentationType());
        System.out.println("[DEBUG] DELETE - Is Replicated: " + tableMeta.isReplicated());
        System.out.println("[DEBUG] DELETE - Worker IDs: " + tableMeta.getWorkerIds());
        
        // For DELETE operations, we need to consider WHERE clause
        String whereClause = queryParser.extractWhereClause(query);
        
        if (tableMeta.isReplicated()) {
            System.out.println("[DELETE Routing] Table is REPLICATED - deleting from all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.HORIZONTAL) {
            List<String> targetWorkers = determineHorizontalFragmentWorkers(query, tableMeta, whereClause);
            if (!targetWorkers.isEmpty()) {
                System.out.println("[DELETE Routing] HORIZONTAL fragmentation - deleting from workers: " + targetWorkers);
                return targetWorkers;
            } else {
                System.out.println("[Warning] Could not determine target workers for horizontal fragmentation - using all workers");
                return tableMeta.getWorkerIds();
            }
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.VERTICAL) {
            System.out.println("[DELETE Routing] VERTICAL fragmentation - deleting from all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.NONE && !tableMeta.isReplicated()) {
            System.out.println("[DELETE Routing] No distribution strategy - deleting from all workers: " + tableMeta.getWorkerIds());
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
            System.out.println("[DELETE Routing] No WHERE clause - checking all workers for safety");
            return tableMeta.getWorkerIds();
        }
        
        // Try to extract specific value from WHERE clause
        String fragmentValue = extractFragmentValueFromWhere(whereClause, fragmentColumn);
        
        if (fragmentValue != null) {
            // We have a specific value, find the exact worker
            System.out.println("[DEBUG] DELETE - Fragment column '" + fragmentColumn + "' value: " + fragmentValue);
            
            for (Map.Entry<String, String> entry : fragmentRanges.entrySet()) {
                String workerId = entry.getKey();
                String condition = entry.getValue();
                
                if (evaluateFragmentConditionString(fragmentValue, condition, fragmentColumn)) {
                    System.out.println("[DEBUG] DELETE - Value '" + fragmentValue + "' matches worker " + workerId + " condition: " + condition);
                    return List.of(workerId);
                }
            }
        }
        
        // If we can't determine specific worker, check all workers for safety
        System.out.println("[DELETE Routing] Cannot determine specific worker - using all workers for safety");
        return tableMeta.getWorkerIds();
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
            System.out.println("[DEBUG] DELETE - Comparing '" + value + "' with '" + expectedValue + "': " + matches);
            return matches;
        }
        
        // Fallback to numeric evaluation for backward compatibility
        try {
            int numericValue = Integer.parseInt(value);
            return evaluateFragmentCondition(numericValue, condition, columnName);
        } catch (NumberFormatException e) {
            System.out.println("[Warning] Could not evaluate DELETE condition for string value: " + value + " with condition: " + condition);
            return false;
        }
    }
}