package org.example.service;

import org.example.model.FragmentationType;
import org.example.model.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertRouter {
    
    private final MetadataManager metadataManager;
    private final QueryParser queryParser;
    
    public InsertRouter(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
        this.queryParser = new QueryParser();
    }
    
    public List<String> determineTargetWorkers(String query, String tableName, String currentDatabase) {
        TableMetadata tableMeta = metadataManager.getMetadata().getTableMetadata(currentDatabase, tableName);
        
        if (tableMeta == null) {
            System.out.println("[Warning] No metadata found for table: " + currentDatabase + "." + tableName);
            return new ArrayList<>();
        }
        
        System.out.println("[DEBUG] Table: " + tableName);
        System.out.println("[DEBUG] Fragmentation Type: " + tableMeta.getFragmentationType());
        System.out.println("[DEBUG] Is Replicated: " + tableMeta.isReplicated());
        System.out.println("[DEBUG] Worker IDs: " + tableMeta.getWorkerIds());
        
        if (tableMeta.isReplicated()) {
            System.out.println("[INSERT Routing] Table is REPLICATED - inserting to all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.HORIZONTAL) {
            String targetWorker = determineHorizontalFragmentWorker(query, tableMeta);
            if (targetWorker != null) {
                System.out.println("[INSERT Routing] HORIZONTAL fragmentation - inserting to worker: " + targetWorker);
                return List.of(targetWorker);
            } else {
                System.out.println("[Warning] Could not determine target worker for horizontal fragmentation");
                return tableMeta.getWorkerIds();
            }
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.VERTICAL) {
            System.out.println("[INSERT Routing] VERTICAL fragmentation - inserting to all workers: " + tableMeta.getWorkerIds());
            return tableMeta.getWorkerIds();
        }
        
        if (tableMeta.getFragmentationType() == FragmentationType.NONE && !tableMeta.isReplicated()) {
            System.out.println("[INSERT Routing] No distribution strategy - inserting to first worker: " + tableMeta.getWorkerIds().get(0));
            return List.of(tableMeta.getWorkerIds().get(0));
        }
        
        return tableMeta.getWorkerIds();
    }
    
    private String determineHorizontalFragmentWorker(String query, TableMetadata tableMeta) {
        Map<String, String> fragmentRanges = tableMeta.getFragmentRanges();
        String fragmentColumn = tableMeta.getFragmentationColumn();
        
        if (fragmentRanges == null || fragmentColumn == null) {
            return null;
        }
        
        String fragmentValue = extractFragmentColumnValueAsString(query, tableMeta);
        
        if (fragmentValue == null) {
            System.out.println("[Warning] Could not extract value for fragmentation column: " + fragmentColumn);
            return null;
        }
        
        System.out.println("[DEBUG] Fragment column '" + fragmentColumn + "' value: " + fragmentValue);
        
        for (Map.Entry<String, String> entry : fragmentRanges.entrySet()) {
            String workerId = entry.getKey();
            String condition = entry.getValue();
            
            if (evaluateFragmentConditionString(fragmentValue, condition, fragmentColumn)) {
                System.out.println("[DEBUG] Value '" + fragmentValue + "' matches worker " + workerId + " condition: " + condition);
                return workerId;
            }
        }
        
        System.out.println("[Warning] No matching fragment range found for value: " + fragmentValue);
        return null;
    }
    
    private String extractFragmentColumnValueAsString(String query, TableMetadata tableMeta) {
        String fragmentColumn = tableMeta.getFragmentationColumn();
        String[] values = queryParser.extractInsertValues(query);
        
        if (values.length == 0) {
            return null;
        }
        
        // Try to find the fragment column value by position or pattern
        // For INSERT INTO table VALUES (...), we need to know column positions
        // For INSERT INTO table (col1, col2, ...) VALUES (...), we can match by name
        
        // First, try to extract from INSERT with column specification
        String columnValue = extractValueFromInsertWithColumns(query, fragmentColumn);
        if (columnValue != null) {
            return columnValue.trim().replace("'", "").replace("\"", "");
        }
        
        // For INSERT without column specification, try to determine position
        // This is a simplified approach - in a real system, you'd need table schema
        if (fragmentColumn.equalsIgnoreCase("Apt_type")) {
            // Apt_type is the 8th column in the Appointment table
            if (values.length >= 8) {
                return values[7].trim().replace("'", "").replace("\"", "");
            }
        } else if (fragmentColumn.equalsIgnoreCase("id") || fragmentColumn.equalsIgnoreCase("Apt_id")) {
            // ID is typically the first column
            if (values.length >= 1) {
                return values[0].trim().replace("'", "").replace("\"", "");
            }
        }
        
        return null;
    }
    
    private String extractValueFromInsertWithColumns(String query, String fragmentColumn) {
        // Look for INSERT INTO table (col1, col2, ...) VALUES (val1, val2, ...)
        Pattern pattern = Pattern.compile("INSERT\\s+INTO\\s+\\w+\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        
        if (matcher.find()) {
            String columnsStr = matcher.group(1);
            String valuesStr = matcher.group(2);
            
            String[] columns = columnsStr.split(",");
            String[] values = valuesStr.split(",");
            
            for (int i = 0; i < columns.length && i < values.length; i++) {
                if (columns[i].trim().equalsIgnoreCase(fragmentColumn)) {
                    return values[i].trim().replace("'", "").replace("\"", "");
                }
            }
        }
        
        return null;
    }
    
    private boolean evaluateFragmentConditionString(String value, String condition, String columnName) {
        condition = condition.trim();
        
        // Handle string equality conditions like "Apt_type = 'doctor'"
        Pattern equalityPattern = Pattern.compile(columnName + "\\s*=\\s*['\"]?([^'\"\\s]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = equalityPattern.matcher(condition);
        
        if (matcher.find()) {
            String expectedValue = matcher.group(1);
            boolean matches = value.equalsIgnoreCase(expectedValue);
            System.out.println("[DEBUG] Comparing '" + value + "' with '" + expectedValue + "': " + matches);
            return matches;
        }
        
        // Fallback to numeric evaluation for backward compatibility
        try {
            int numericValue = Integer.parseInt(value);
            return evaluateFragmentCondition(numericValue, condition, columnName);
        } catch (NumberFormatException e) {
            System.out.println("[Warning] Could not evaluate condition for string value: " + value + " with condition: " + condition);
            return false;
        }
    }
    
    private Integer extractFragmentColumnValue(String query, TableMetadata tableMeta) {
        String stringValue = extractFragmentColumnValueAsString(query, tableMeta);
        if (stringValue != null) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                return null;
            }
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
}
