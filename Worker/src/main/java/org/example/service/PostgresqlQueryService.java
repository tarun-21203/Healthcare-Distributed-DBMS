package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.POJOs.SelectResponse;
import org.example.constant.SQLCommandConstant;
import org.example.exception.FileIOException;
import org.example.interfaces.IDatabaseManager;
import org.example.interfaces.IQueryManager;
import org.example.model.Column;
import org.example.model.Condition;
import org.example.model.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostgresqlQueryService implements IQueryManager {

    private final IDatabaseManager databaseManagerService;

    public PostgresqlQueryService() throws FileIOException, JsonProcessingException {
        this.databaseManagerService = new DatabaseManagerService();
    }

    private List<String> breakdownQuery(String query) {
        return List.of(query.trim().split("\\s+"));
    }

    private List<String> splitSubQuery(String query, int startIndex, int endIndex, String regex) {
        return Arrays.stream(query.substring(startIndex, endIndex == -1 ? query.length() : endIndex).trim().split(regex)).map(String::trim).toList();
    }

    private Condition parseCondition(String subQuery) {
        Pattern pattern = Pattern.compile("(\\w+)\\s*(=|like|<|>|<=|>=|!=)\\s*([^,]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subQuery);
        while (matcher.find()) {
            String key = matcher.group(1);
            String operator = matcher.group(2);
            String value = matcher.group(3);
            return new Condition(key, operator.toLowerCase(), value.trim().replace("'", ""));
        }
        return null;
    }

    private String extractUserFromQuery(String query) {
        Pattern pattern = Pattern.compile("\\s+AS\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "defaultUser";
    }

    private String removeUserClauseFromQuery(String query) {
        Pattern pattern = Pattern.compile("\\s+AS\\s+\\w+\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            return query.substring(0, matcher.start()).trim();
        }
        return query.trim();
    }

    @Override
    public SelectResponse queryParser(String queries) throws FileIOException, IOException {
        List<String> queriesList = splitSubQuery(queries, 0, -1, ";");
        for (String query : queriesList) {

            String user = extractUserFromQuery(query);
            query = removeUserClauseFromQuery(query);

            List<String> breakDownQuery = breakdownQuery(query.toLowerCase());
            if (breakDownQuery.isEmpty()) {
                System.out.println("Invalid query.");
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.BEGIN.getCommand()) ||
                    breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.COMMIT.getCommand()) ||
                    breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.ROLLBACK.getCommand())) {
                handleTransaction(query, user);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.SHOW.getCommand())) {
                if (breakDownQuery.get(1).equals(SQLCommandConstant.SQLKeyword.DATABASES.getKeyword())) {
                    showDatabases(query);
                }
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.USE.getCommand())) {
                useDatabase(query);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.CREATE.getCommand())) {
                if (breakDownQuery.get(1).equals(SQLCommandConstant.SQLKeyword.DATABASE.getKeyword())) {
                    createDatabase(query);
                } else if (breakDownQuery.get(1).equals(SQLCommandConstant.SQLKeyword.TABLE.getKeyword())) {
                    createTable(query);
                }
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.DESCRIBE.getCommand())) {
                describeTable(query);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.SELECT.getCommand())) {
                return selectTable(query, user);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.INSERT.getCommand())) {
                insertTable(query, user);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.UPDATE.getCommand())) {
                updateTable(query, user);
            } else if (breakDownQuery.get(0).equals(SQLCommandConstant.SQLCommand.DELETE.getCommand())) {
                deleteRow(query, user);
            } else {
                System.out.println("Unknown query type.");
            }
        }
        return null;
    }

    private void createDatabase(String query) throws FileIOException, JsonProcessingException {
        List<String> subQuery = breakdownQuery(query);
        if (subQuery.size() == 3) {
            databaseManagerService.createDatabase(subQuery.get(2));
            System.out.println("Database created successfully");
        } else throw new IllegalArgumentException("Syntax error in CREATE DATABASE statement.");
    }

    private void showDatabases(String query) {
        List<String> subQuery = breakdownQuery(query);
        if (subQuery.size() == 2) {
            List<String> databases = databaseManagerService.showDatabase();
            if (databases.isEmpty()) {
                throw new IllegalArgumentException("No Database Found");
            } else {
                System.out.println("\n+----------------------+");
                System.out.println("| Databases            |");
                System.out.println("+----------------------+");
                for (String db : databases) {
                    System.out.printf("| %-20s |\n", db);
                }
                System.out.println("+----------------------+");
            }
        } else throw new IllegalArgumentException("No Database Found");
    }

    private void useDatabase(String query) throws FileIOException {
        List<String> subQuery = breakdownQuery(query);
        if (subQuery.size() == 2) {
            databaseManagerService.useDatabase(subQuery.get(1));
            System.out.printf("Using %s Database\n", subQuery.get(1));
        } else throw new IllegalArgumentException("Syntax error in USE statement.");
    }

    private void createTable(String query) throws FileIOException, JsonProcessingException {
        int bracketStart = query.indexOf('(');
        int bracketEnd = query.lastIndexOf(')');
        if (bracketStart == -1 || bracketEnd == -1) {
            throw new IllegalArgumentException("Syntax error in CREATE TABLE statement.");
        }

        List<String> tablePart = breakdownQuery(query.substring(0, bracketStart));
        if (tablePart.size() != 3) {
            throw new IllegalArgumentException("Syntax error in CREATE TABLE statement.");
        }
        String name = tablePart.get(2);

        String columnPart = query.substring(bracketStart + 1, bracketEnd);
        List<String> columns = splitSubQuery(columnPart, 0, -1, ",");

        List<Column> columnList = new ArrayList<>();
        for (String column : columns) {
            List<String> columnData = breakdownQuery(column);
            if (columnData.size() != 2) {
                throw new IllegalArgumentException("Syntax error in CREATE TABLE statement.");
            } else if (!isValidDataType(columnData.get(1).toUpperCase())) {
                throw new IllegalArgumentException("Syntax error in CREATE TABLE statement.");
            }
            columnList.add(new Column(columnData.get(0), Type.valueOf(columnData.get(1).toUpperCase())));
        }
        databaseManagerService.createTable(name, columnList);
        System.out.println("Table Created Successfully");
    }

    private void insertTable(String query, String user) throws FileIOException {
        String lowerCaseQuery = query.toLowerCase();
        int bracketStart = lowerCaseQuery.indexOf('(');
        int bracketEnd = lowerCaseQuery.lastIndexOf(')');
        if (bracketStart == -1 || bracketEnd == -1) {
            throw new IllegalArgumentException("Syntax error in INSERT TABLE statement.");
        }
        List<String> tablePart = breakdownQuery(lowerCaseQuery.substring(0, bracketStart));
        if (tablePart.size() != 4 || !tablePart.get(1).equals(SQLCommandConstant.SQLKeyword.INTO.getKeyword()) || !tablePart.get(3).equals(SQLCommandConstant.SQLKeyword.VALUES.getKeyword())) {
            throw new IllegalArgumentException("Syntax error in INSERT TABLE statement.");
        }
        String tableName = tablePart.get(2);
        String columnPart = query.substring(bracketStart + 1, bracketEnd);
        List<String> columnDataList = new ArrayList<>(splitSubQuery(columnPart, 0, -1, ","));
        columnDataList.replaceAll(string -> string.trim().replace("'", ""));
        databaseManagerService.insertRow(tableName, columnDataList, user);
        System.out.printf("Added Row to %s\n", tableName);
    }

    private void updateTable(String query, String user) throws FileIOException {
        String lowerCaseQuery = query.toLowerCase();
        List<String> subQuery = breakdownQuery(lowerCaseQuery);
        if (!subQuery.get(2).equals(SQLCommandConstant.SQLKeyword.SET.getKeyword())) {
            throw new IllegalArgumentException("Syntax error in UPDATE TABLE statement.");
        }
        String tableName = subQuery.get(1);
        int setIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.SET.getKeyword());
        int whereIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.WHERE.getKeyword());
        if (whereIndex == -1) {
            throw new IllegalArgumentException("No where found: Critical Operation.");
        }
        List<String> setPart = splitSubQuery(query, setIndex + 3, whereIndex, "=");
        Condition whereCondition = parseCondition(query.substring(whereIndex + 5));

        if (setPart.size() != 2) {
            throw new IllegalArgumentException("Invalid Set Condition.");
        }

        if (whereCondition == null) {
            throw new IllegalArgumentException("Invalid Where Condition.");
        }

        Condition setCondition = new Condition(setPart.get(0).toLowerCase(), "=", setPart.get(1));
        databaseManagerService.updateColumn(tableName, setCondition, whereCondition, user);
        System.out.println("Data has been updated successfully");
    }

    private void deleteRow(String query, String user) throws FileIOException {
        String lowerCaseQuery = query.toLowerCase();
        int fromIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.FROM.getKeyword());
        int whereIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.WHERE.getKeyword());

        if (fromIndex == -1 || whereIndex == -1)
            throw new IllegalArgumentException("Syntax error in DELETE statement.");

        String tableName = lowerCaseQuery.substring(fromIndex + 4, whereIndex).trim();

        Condition whereCondition = parseCondition(query.substring(whereIndex + 5));
        if (whereCondition == null) {
            throw new IllegalArgumentException("Invalid Where Condition.");
        }
        databaseManagerService.deleteRow(tableName, whereCondition, user);
        System.out.println("Data has been deleted successfully");

    }

    private SelectResponse selectTable(String query, String user) throws FileIOException {
        System.out.println("\nSELECT");
        String lowerCaseQuery = query.toLowerCase();
        int fromIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.FROM.getKeyword());
        int whereIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.WHERE.getKeyword());

        if (fromIndex == -1) {
            throw new IllegalArgumentException("Syntax Error in SELECT statement");
        }
        List<String> columns = splitSubQuery(lowerCaseQuery, 6, fromIndex, ",");
        String tableName = lowerCaseQuery.substring(fromIndex + 4, whereIndex != -1 ? whereIndex : lowerCaseQuery.length()).trim();

        Condition whereCondition;
        if (whereIndex == -1) {
            whereCondition = null;
        } else {
            whereCondition = parseCondition(query.substring(whereIndex + 5));
        }
        SelectResponse data = databaseManagerService.searchRow(tableName, columns, whereCondition, user);
        printTable(data);
        return data;
    }

    private void describeTable(String query) {
        List<String> columns = splitSubQuery(query, 0, -1, " ");
        if (columns.size() != 2) {
            throw new IllegalArgumentException("Syntax error in DESCRIBE statement.");
        }
        List<Column> columnList = databaseManagerService.describeTable(columns.get(1));
        System.out.printf("Table %s contain following attributes \n", columns.get(1));

        for (Column column : columnList) {
            System.out.printf("%s (%s)\n", column.getName(), column.getType());
        }
    }

    private void handleTransaction(String query, String user) throws FileIOException, IOException {
        List<String> breakDownQuery = breakdownQuery(query);
        SQLCommandConstant.SQLCommand command = SQLCommandConstant.SQLCommand.valueOf(breakDownQuery.get(0).toUpperCase());
        switch (command) {
            case BEGIN:
                databaseManagerService.startTransaction(user);
                break;
            case COMMIT:
                databaseManagerService.commitTransaction(user);
                break;
            case ROLLBACK:
                databaseManagerService.rollbackTransaction(user);
                break;
            default:
                throw new IllegalArgumentException("Invalid SQL command.");
        }
    }

    private void printTable(SelectResponse selectData) {
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

    private static boolean isValidDataType(String value) {
        try {
            Type.valueOf(value.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
