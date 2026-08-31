package org.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryParser {
    
    public String extractTableName(String query) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.contains("FROM")) {
            Pattern pattern = Pattern.compile("FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        if (upperQuery.contains("INSERT INTO")) {
            Pattern pattern = Pattern.compile("INSERT\\s+INTO\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        if (upperQuery.startsWith("UPDATE")) {
            Pattern pattern = Pattern.compile("UPDATE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        if (upperQuery.contains("DELETE FROM")) {
            Pattern pattern = Pattern.compile("DELETE\\s+FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        if (upperQuery.contains("DROP TABLE")) {
            Pattern pattern = Pattern.compile("DROP\\s+TABLE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    public String extractTableNameFromCreate(String query) {
        Pattern pattern = Pattern.compile("CREATE\\s+TABLE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    public String extractWhereClause(String query) {
        int whereIndex = query.toUpperCase().indexOf("WHERE");
        if (whereIndex != -1) {
            return query.substring(whereIndex + 5).trim();
        }
        return null;
    }
    
    public String determineOperation(String query) {
        String upperQuery = query.trim().toUpperCase();
        if (upperQuery.startsWith("SELECT")) return "SELECT";
        if (upperQuery.startsWith("INSERT")) return "INSERT";
        if (upperQuery.startsWith("UPDATE")) return "UPDATE";
        if (upperQuery.startsWith("DELETE")) return "DELETE";
        if (upperQuery.startsWith("CREATE")) return "CREATE";
        if (upperQuery.startsWith("DROP")) return "DROP";
        if (upperQuery.startsWith("USE")) return "USE";
        if (upperQuery.startsWith("SHOW")) return "SHOW";
        if (upperQuery.startsWith("DESCRIBE")) return "DESCRIBE";
        if (upperQuery.startsWith("REPLICATE")) return "REPLICATE";
        if (upperQuery.startsWith("FRAGMENT")) return "FRAGMENT";
        return "UNKNOWN";
    }
    
    public boolean isValidQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String operation = determineOperation(query);
        return !operation.equals("UNKNOWN");
    }
    
    public String validateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "Error: Empty query provided";
        }
        
        String trimmedQuery = query.trim();
        
        // Check for completely nonsensical input
        if (isNonsensicalInput(trimmedQuery)) {
            return "Error: Invalid input. Please enter a valid SQL command.";
        }
        
        // Check for common typos and invalid characters
        if (containsInvalidCharacters(trimmedQuery)) {
            return "Error: Query contains invalid characters or formatting";
        }
        
        String operation = determineOperation(trimmedQuery);
        
        if (operation.equals("UNKNOWN")) {
            // Provide more specific error for common typos
            String suggestion = suggestCorrection(trimmedQuery);
            if (suggestion != null) {
                return "Error: Unknown SQL command '" + trimmedQuery.split("\\s+")[0] + "'. Did you mean '" + suggestion + "'?";
            }
            return "Error: Unknown SQL command '" + trimmedQuery.split("\\s+")[0] + "'. Supported commands: SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, USE, SHOW, DESCRIBE, REPLICATE, FRAGMENT";
        }
        
        // Basic syntax validation for each operation
        switch (operation) {
            case "SELECT":
                return validateSelectQuery(trimmedQuery);
            case "INSERT":
                return validateInsertQuery(trimmedQuery);
            case "UPDATE":
                return validateUpdateQuery(trimmedQuery);
            case "DELETE":
                return validateDeleteQuery(trimmedQuery);
            case "CREATE":
                return validateCreateQuery(trimmedQuery);
            case "DROP":
                return validateDropQuery(trimmedQuery);
            case "USE":
                return validateUseQuery(trimmedQuery);
            case "SHOW":
                return validateShowQuery(trimmedQuery);
            case "DESCRIBE":
                return validateDescribeQuery(trimmedQuery);
            case "REPLICATE":
                return validateReplicateQuery(trimmedQuery);
            case "FRAGMENT":
                return validateFragmentQuery(trimmedQuery);
            default:
                return null; // Valid
        }
    }
    
    private String validateSelectQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (!upperQuery.contains("FROM")) {
            return "Error: SELECT query must contain FROM clause";
        }
        
        // Check for basic SELECT structure
        if (!upperQuery.startsWith("SELECT ")) {
            return "Error: SELECT must be followed by column names or *";
        }
        
        String tableName = extractTableName(query);
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Invalid or missing table name in SELECT query";
        }
        
        // Check for invalid SELECT patterns
        if (upperQuery.contains("SELECT FROM")) {
            return "Error: SELECT must specify columns (use * for all columns)";
        }
        
        return null; // Valid
    }
    
    private String validateInsertQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (!upperQuery.contains("INSERT INTO")) {
            return "Error: INSERT query must use 'INSERT INTO' syntax";
        }
        
        if (!upperQuery.contains("VALUES")) {
            return "Error: INSERT query must contain VALUES clause";
        }
        
        String tableName = extractTableName(query);
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Invalid or missing table name in INSERT query";
        }
        
        // Check for VALUES format
        if (!query.contains("(") || !query.contains(")")) {
            return "Error: INSERT VALUES must be enclosed in parentheses";
        }
        
        // Check for proper INSERT INTO format
        if (upperQuery.matches(".*INSERT\\s+INTO\\s*$")) {
            return "Error: INSERT INTO must be followed by table name";
        }
        
        return null; // Valid
    }
    
    private String validateUpdateQuery(String query) {
        if (!query.toUpperCase().contains("SET")) {
            return "Error: UPDATE query must contain SET clause";
        }
        
        String tableName = extractTableName(query);
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Invalid or missing table name in UPDATE query";
        }
        
        return null; // Valid
    }
    
    private String validateDeleteQuery(String query) {
        if (!query.toUpperCase().contains("DELETE FROM")) {
            return "Error: DELETE query must use 'DELETE FROM' syntax";
        }
        
        String tableName = extractTableName(query);
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Invalid or missing table name in DELETE query";
        }
        
        return null; // Valid
    }
    
    private String validateCreateQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.contains("CREATE TABLE")) {
            if (!query.contains("(") || !query.contains(")")) {
                return "Error: CREATE TABLE must specify column definitions in parentheses";
            }
            
            String tableName = extractTableNameFromCreate(query);
            if (tableName == null || tableName.trim().isEmpty()) {
                return "Error: Invalid or missing table name in CREATE TABLE query";
            }
        } else if (upperQuery.contains("CREATE DATABASE")) {
            String[] parts = query.trim().split("\\s+");
            if (parts.length < 3) {
                return "Error: CREATE DATABASE requires database name";
            }
        } else {
            return "Error: CREATE command must specify TABLE or DATABASE";
        }
        
        return null; // Valid
    }
    
    private String validateDropQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.contains("DROP TABLE")) {
            String tableName = extractTableName(query);
            if (tableName == null || tableName.trim().isEmpty()) {
                return "Error: Invalid or missing table name in DROP TABLE query";
            }
        } else if (upperQuery.contains("DROP DATABASE")) {
            String[] parts = query.trim().split("\\s+");
            if (parts.length < 3) {
                return "Error: DROP DATABASE requires database name";
            }
        } else {
            return "Error: DROP command must specify TABLE or DATABASE";
        }
        
        return null; // Valid
    }
    
    private String validateUseQuery(String query) {
        String[] parts = query.trim().split("\\s+");
        if (parts.length < 2) {
            return "Error: USE command requires database name";
        }
        
        return null; // Valid
    }
    
    private String validateShowQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (upperQuery.equals("SHOW METADATA")) {
            return null; // Valid
        }
        
        if (upperQuery.contains("SHOW DATABASES") || upperQuery.contains("SHOW TABLES")) {
            return null; // Valid
        }
        
        return "Error: SHOW command supports: SHOW DATABASES, SHOW TABLES, SHOW METADATA";
    }
    
    private String validateDescribeQuery(String query) {
        String[] parts = query.trim().split("\\s+");
        if (parts.length < 2) {
            return "Error: DESCRIBE command requires table name";
        }
        
        return null; // Valid
    }
    
    private String validateReplicateQuery(String query) {
        if (!query.toUpperCase().contains("REPLICATE TABLE")) {
            return "Error: REPLICATE command must use 'REPLICATE TABLE' syntax";
        }
        
        if (!query.toUpperCase().contains(" ON ")) {
            return "Error: REPLICATE TABLE must specify workers using ON clause";
        }
        
        return null; // Valid
    }
    
    private String validateFragmentQuery(String query) {
        String upperQuery = query.toUpperCase();
        
        if (!upperQuery.contains("FRAGMENT TABLE")) {
            return "Error: FRAGMENT command must use 'FRAGMENT TABLE' syntax";
        }
        
        if (upperQuery.contains("HORIZONTAL")) {
            if (!upperQuery.contains(" ON ") || !upperQuery.contains("RANGE")) {
                return "Error: HORIZONTAL fragmentation requires ON column and RANGE specification";
            }
        } else if (upperQuery.contains("VERTICAL")) {
            if (!upperQuery.contains("COLUMNS")) {
                return "Error: VERTICAL fragmentation requires COLUMNS specification";
            }
        } else {
            return "Error: FRAGMENT TABLE must specify HORIZONTAL or VERTICAL fragmentation";
        }
        
        return null; // Valid
    }
    
    public List<String> extractColumnNames(String columnsStr) {
        List<String> columnNames = new ArrayList<>();
        String[] columnDefs = columnsStr.split(",");
        
        for (String columnDef : columnDefs) {
            String[] parts = columnDef.trim().split("\\s+");
            if (parts.length > 0) {
                columnNames.add(parts[0].trim());
            }
        }
        
        return columnNames;
    }
    
    public String[] extractInsertValues(String query) {
        Pattern valuesPattern = Pattern.compile("VALUES\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher valuesMatcher = valuesPattern.matcher(query);
        
        if (valuesMatcher.find()) {
            String valuesStr = valuesMatcher.group(1);
            return valuesStr.split(",");
        }
        
        return new String[0];
    }
    
    private boolean containsInvalidCharacters(String query) {
        // Check for obviously invalid patterns
        if (query.contains(";;") || query.startsWith(";")) {
            return true;
        }
        
        // Check for nonsensical input (too many special characters)
        long specialCharCount = query.chars().filter(c -> "!@#$%^&*()_+-=[]{}|\\:;\"'<>?,./".indexOf(c) >= 0).count();
        if (specialCharCount > query.length() / 2) {
            return true;
        }
        
        // Check for unmatched parentheses
        int openParens = 0;
        for (char c : query.toCharArray()) {
            if (c == '(') openParens++;
            if (c == ')') openParens--;
            if (openParens < 0) return true; // More closing than opening
        }
        
        // Check for unmatched quotes
        int singleQuotes = 0;
        int doubleQuotes = 0;
        for (char c : query.toCharArray()) {
            if (c == '\'') singleQuotes++;
            if (c == '"') doubleQuotes++;
        }
        
        return openParens != 0 || singleQuotes % 2 != 0 || doubleQuotes % 2 != 0;
    }
    
    private String suggestCorrection(String query) {
        String firstWord = query.trim().split("\\s+")[0].toUpperCase();
        
        // Common typos and their corrections
        switch (firstWord) {
            case "SELCT":
            case "SLECT":
            case "SELET":
                return "SELECT";
            case "INSRT":
            case "INSER":
                return "INSERT";
            case "UPDAT":
            case "UPDAE":
                return "UPDATE";
            case "DELET":
            case "DELEET":
                return "DELETE";
            case "CREAT":
            case "CRAETE":
                return "CREATE";
            case "DRPO":
            case "DORP":
                return "DROP";
            case "SHWO":
            case "SOHW":
                return "SHOW";
            case "DESCRIB":
            case "DESRIBE":
                return "DESCRIBE";
            default:
                return null;
        }
    }
    
    private boolean isNonsensicalInput(String query) {
        // Check for very short nonsensical input
        if (query.length() < 3) {
            return true;
        }
        
        // Check if input is just numbers or special characters
        if (query.matches("^[0-9!@#$%^&*()_+\\-=\\[\\]{}|\\\\:;\"'<>?,./\\s]+$")) {
            return true;
        }
        
        // Check if input has no alphabetic characters
        if (!query.matches(".*[a-zA-Z].*")) {
            return true;
        }
        
        // Check for random keyboard mashing (too many consecutive consonants/vowels)
        if (query.matches(".*[bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ]{6,}.*") ||
            query.matches(".*[aeiouAEIOU]{5,}.*")) {
            return true;
        }
        
        return false;
    }
}
