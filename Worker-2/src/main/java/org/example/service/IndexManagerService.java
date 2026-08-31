package org.example.service;

import org.example.constant.AppConstant;
import org.example.constant.SQLCommandConstant;
import org.example.exception.FileIOException;
import org.example.interfaces.IIndexManager;
import org.example.model.Column;
import org.example.model.Condition;
import org.example.model.Table;
import org.example.model.Type;
import org.example.util.FileIOUtility.TextFileIOUtil;
import org.example.util.TreeUtility.BST;
import org.example.util.TreeUtility.ITreeUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexManagerService implements IIndexManager {

    private final Map<String, ITreeUtility<Integer, Map<String, Object>>> tableIndexes;
    private final TextFileIOUtil fileIOUtil;

    public IndexManagerService() {
        tableIndexes = new HashMap<>();
        fileIOUtil = new TextFileIOUtil();
    }

    private void insertIndexes(String tableName, Integer index, Map value) {
        if (!tableIndexes.containsKey(tableName)) {
            throw new IllegalArgumentException("Table Index doesn't exists");
        }
        tableIndexes.get(tableName).insert(index, value);
    }

    private void updateDataToFile(String tableName, Map<String, Table> tableMeta) throws FileIOException {
        StringBuilder stringData = new StringBuilder();
        List<Map<String, Object>> allData = tableIndexes.get(tableName).getAllNodes();

        List<Column> columnMetaData = tableMeta.get(tableName).getColumns();

        for (Map<String, Object> rowData : allData) {
            for (Column column : columnMetaData) {
                stringData.append(rowData.get(column.getName()));
                stringData.append(AppConstant.Delimiters.COLUMN_DELIMITER);
            }
            stringData.append(AppConstant.Delimiters.ROW_DELIMITER);
            stringData.append("\n");
        }
        fileIOUtil.writeFileAsString(AppConstant.FilePaths.DATA_FILE_PATH + tableName + AppConstant.FileType.TXT, stringData.toString());
    }

    @Override
    public void refreshStorage(Map<String, Table> tableMeta) throws FileIOException {
        for (String tableName : tableIndexes.keySet()) {
            updateDataToFile(tableName, tableMeta);
        }
    }

    @Override
    public void fetchIndexes(Map<String, Table> tableMeta) throws FileIOException {
        List<String> tableNames = tableMeta.keySet().stream().toList();
        System.out.println("Fetching indexes for table: " + tableNames);
        for (String tableName : tableNames) {
            System.out.println("Fetching indexes for table: " + tableName);
            createIndexes(tableName);
            String fileData = fileIOUtil.readFileAsString(AppConstant.FilePaths.DATA_FILE_PATH + tableName + AppConstant.FileType.TXT);
            List<String> dataRows = List.of(fileData.trim().split(AppConstant.Delimiters.ROW_DELIMITER));
            for (String row : dataRows) {
                if (!row.isEmpty()) {
                    List<String> columnData = new ArrayList<>(List.of(row.split(AppConstant.Delimiters.COLUMN_DELIMITER)));
                    Integer index = Integer.parseInt(columnData.get(0));
                    insertToIndex(tableName, index, columnData, tableMeta);
                }
            }
        }
    }

    @Override
    public void createIndexes(String name) {
        tableIndexes.put(name, new BST<Integer, Map<String, Object>>());
    }

    @Override
    public void updateToIndex(String tableName, Integer index, String column, String value, Map<String, Table> tableMeta, Boolean isTransaction) throws FileIOException {

        Map<String, Object> map = (Map<String, Object>) tableIndexes.get(tableName).search(index);
        if (map == null) {
            throw new IllegalArgumentException("No data found");
        }
        map.put(column, value);
        if (!tableIndexes.get(tableName).update(index, map)) {
            throw new IllegalArgumentException("Update failed");
        }
        if (!isTransaction)
            updateDataToFile(tableName, tableMeta);
    }

    @Override
    public void insertToIndex(String tableName, Integer index, List<String> columns, Map<String, Table> tableMeta) {
        if (!tableIndexes.containsKey(tableName)) {
            throw new IllegalArgumentException("Table Not Exists");
        }
        if (tableIndexes.get(tableName).search(index) != null) {
            throw new IllegalArgumentException("Entry Already Exists");
        }
        List<Column> columnsMeta = tableMeta.get(tableName).getColumns();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < columnsMeta.size(); i++) {
            validateDataType(columns.get(i), columnsMeta.get(i).type);
            map.put(columnsMeta.get(i).getName(), columns.get(i));
        }
        insertIndexes(tableName, index, map);
    }

    @Override
    public void deleteToIndex(String tableName, Integer index, Map<String, Table> tableMeta, Boolean isTransaction) throws FileIOException {
        Map<String, Object> map = (Map<String, Object>) tableIndexes.get(tableName).search(index);
        if (map == null) {
            throw new IllegalArgumentException("No data found");
        }
        if (!tableIndexes.get(tableName).delete(index)) {
            throw new IllegalArgumentException("Delete failed");
        }
        if (!isTransaction)
            updateDataToFile(tableName, tableMeta);
    }

    @Override
    public Map<String, Object> searchToIndex(String tableName, Integer index) {
        Map<String, Object> map = (Map<String, Object>) tableIndexes.get(tableName).search(index);
        if (map == null) {
            throw new IllegalArgumentException("No data found");
        }
        return map;
    }

    @Override
    public List<Map<String, Object>> getAllData(String tableName) {
        return tableIndexes.get(tableName).getAllNodes();
    }

    @Override
    public void storeDataToFile(String tableName, List<String> data) throws FileIOException {
        StringBuilder stringData = new StringBuilder();
        for (String a : data) {
            stringData.append(a);
            stringData.append(AppConstant.Delimiters.COLUMN_DELIMITER);
        }
        stringData.append(AppConstant.Delimiters.ROW_DELIMITER);
        fileIOUtil.appendToFile(AppConstant.FilePaths.DATA_FILE_PATH + tableName + AppConstant.FileType.TXT, stringData.toString());
    }

    @Override
    public int searchIndexInFile(String tableName, Condition condition, Integer columnNumber) throws FileIOException {
        String fileText = fileIOUtil.readFileAsString(AppConstant.FilePaths.DATA_FILE_PATH + tableName + AppConstant.FileType.TXT);
        List<String> rows = List.of(fileText.trim().split(AppConstant.Delimiters.ROW_DELIMITER));
        for (String row : rows) {
            String[] columns = row.split(AppConstant.Delimiters.COLUMN_DELIMITER);
            if (condition.getOperator().equals("=") && columns[columnNumber].trim().equals(condition.getValue().trim())) {
                return Integer.parseInt(columns[0].trim());
            } else if (condition.getOperator().equals(SQLCommandConstant.SQLKeyword.LIKE.getKeyword())) {
                int likeIndex = condition.getValue().indexOf("%");
                String conditionValue = condition.getValue().replace("%", "");
                if (likeIndex == 0 && columns[columnNumber].trim().endsWith(conditionValue))
                    return Integer.parseInt(columns[0].trim());
                if (likeIndex == condition.getValue().length() - 1 && columns[columnNumber].trim().startsWith(conditionValue))
                    return Integer.parseInt(columns[0].trim());
            }
        }
        return -1;
    }

    @Override
    public void validateDataType(String value, Type type) {
        try {
            switch (type) {
                case INT:
                    Integer.parseInt(value);
                    break;
                case FLOAT:
                    Float.parseFloat(value);
                    break;
                case STRING:
                    break;
                default:
                    throw new IllegalArgumentException("Invalid value: " + value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }
    @Override
    public List<Map<String, Object>> searchAllIndexes(String tableName, Condition whereCondition) {
        List<Map<String, Object>> allRows = getAllData(tableName);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : allRows) {
            Object val = row.get(whereCondition.getKey());
            if (val != null && matchesCondition(val.toString().trim(), whereCondition.getOperator(), whereCondition.getValue().trim())) {
                result.add(row);
            }
        }
        return result;
    }

    private boolean matchesCondition(String rowValue, String operator, String conditionValue) {
        switch (operator.toLowerCase()) {
            case "=":
                return rowValue.equals(conditionValue);
            case "!=":
                return !rowValue.equals(conditionValue);
            case "like":
                if (conditionValue.startsWith("%") && conditionValue.endsWith("%")) {
                    return rowValue.contains(conditionValue.substring(1, conditionValue.length() - 1));
                } else if (conditionValue.startsWith("%")) {
                    return rowValue.endsWith(conditionValue.substring(1));
                } else if (conditionValue.endsWith("%")) {
                    return rowValue.startsWith(conditionValue.substring(0, conditionValue.length() - 1));
                } else {
                    return rowValue.equals(conditionValue);
                }
            case "<":
                return compareValues(rowValue, conditionValue) < 0;
            case ">":
                return compareValues(rowValue, conditionValue) > 0;
            case "<=":
                return compareValues(rowValue, conditionValue) <= 0;
            case ">=":
                return compareValues(rowValue, conditionValue) >= 0;
            default:
                return false;
        }
    }

    private int compareValues(String val1, String val2) {
        try {
            // Try to compare as numbers first
            Double num1 = Double.parseDouble(val1);
            Double num2 = Double.parseDouble(val2);
            return num1.compareTo(num2);
        } catch (NumberFormatException e) {
            // If not numbers, compare as strings
            return val1.compareTo(val2);
        }
    }

}
