package org.example.service;

import org.example.constant.AppConstant;
import org.example.constant.SQLCommandConstant;
import org.example.exception.FileIOException;
import org.example.interfaces.IAuditLogger;
import org.example.model.AuditLog;
import org.example.model.Condition;
import org.example.util.FileIOUtility.IFileIOUtil;
import org.example.util.FileIOUtility.TextFileIOUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuditLoggerService implements IAuditLogger {
    IFileIOUtil fileIOUtil;

    public AuditLoggerService() throws FileIOException {
        fileIOUtil = new TextFileIOUtil();
        initializeLogFile();
    }

    private void initializeLogFile() throws FileIOException {
        fileIOUtil.createFile(AppConstant.FilePaths.AUDIT_LOG_FILE_PATH);
    }

    @Override
    public void logAudit(String userId, String message, AppConstant.AUDIT_LOG_TYPE logType) throws FileIOException {
        fileIOUtil.appendToFile(AppConstant.FilePaths.AUDIT_LOG_FILE_PATH,
                String.format("%s,%s,%s,%s,%s",
                        logType.name(),
                        userId,
                        message,
                        generateIPAddress(),
                        currentTimestamp()));
    }

    @Override
    public String lastSuccessfulLogin(String userId) throws FileIOException {
        String lastLoginTime = "First Login";
        for (String line : fileIOUtil.readFromFile(AppConstant.FilePaths.AUDIT_LOG_FILE_PATH)) {
            String[] logData = line.split(AppConstant.Delimiters.AUDIT_LOG_DELIMITER);
            if (logData[0].equals(AppConstant.AUDIT_LOG_TYPE.SUCCESS_LOGIN.name()) && logData[1].equals(userId)) {
                lastLoginTime = logData[4];
            }
        }
        return lastLoginTime;
    }

    @Override
    public void getLogData(String query) throws FileIOException {
        String lowerCaseQuery = Arrays.stream(query.trim().split(";")).map(String::trim).toList().get(0).toLowerCase();

        String selectStatement = List.of(lowerCaseQuery.trim().split("\\s+")).get(0);

        if (!selectStatement.equals(SQLCommandConstant.SQLCommand.SELECT.getCommand())) {
            System.out.println(selectStatement + "||" + SQLCommandConstant.SQLCommand.SELECT.name());
            throw new IllegalArgumentException("Invalid Select Query");
        }

        int fromIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.FROM.getKeyword());
        int whereIndex = lowerCaseQuery.indexOf(SQLCommandConstant.SQLKeyword.WHERE.getKeyword());

        if (fromIndex == -1) {
            throw new IllegalArgumentException("Syntax Error in SELECT statement");
        }
        List<String> columns = Arrays.stream(query.substring(6, fromIndex).trim().split(",")).map(String::trim).toList();

        String tableName = lowerCaseQuery.substring(fromIndex + 4, whereIndex != -1 ? whereIndex : lowerCaseQuery.length()).trim();

        if (!tableName.equals("audit_log")) {
            throw new IllegalArgumentException("No Table Found");
        }
        Condition whereCondition = null;
        if (whereIndex != -1) {
            Pattern pattern = Pattern.compile("(\\w+)\\s*(=)\\s*([^,]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query.substring(whereIndex + 5));
            while (matcher.find()) {
                String key = matcher.group(1);
                String operator = matcher.group(2);
                String value = matcher.group(3);
                whereCondition = new Condition(key, operator.toLowerCase(), value.trim().replace("'", ""));
            }
        }
        List<AuditLog> auditLogList = getAuditLogData(whereCondition);
        if (columns.size() == 1 && columns.get(0).equals("*")) {
            columns = List.of(AppConstant.AuditLogRows.TYPE, AppConstant.AuditLogRows.USER_ID, AppConstant.AuditLogRows.MESSAGE, AppConstant.AuditLogRows.IP_ADDRESS, AppConstant.AuditLogRows.TIMESTAMP);
        }
        printLogs(auditLogList, columns);
    }

    private void printLogs(List<AuditLog> auditLogList, List<String> columns) {
        System.out.println("\n+" + "-".repeat(columns.size() * 30 + 1) + "+");
        for (String columnName : columns) {
            System.out.printf("| %-28s ", columnName);
        }
        System.out.println("|\n+" + "-".repeat(columns.size() * 30 + 1) + "+");
        for (AuditLog data : auditLogList) {
            for (String columnName : columns) {
                switch (columnName) {
                    case AppConstant.AuditLogRows.TYPE:
                        System.out.printf("| %-28s ", data.getLogType());
                        break;
                    case AppConstant.AuditLogRows.USER_ID:
                        System.out.printf("| %-28s ", data.getUserId());
                        break;
                    case AppConstant.AuditLogRows.IP_ADDRESS:
                        System.out.printf("| %-28s ", data.getIpAddress());
                        break;
                    case AppConstant.AuditLogRows.MESSAGE:
                        System.out.printf("| %-28s ", data.getMessage());
                        break;
                    case AppConstant.AuditLogRows.TIMESTAMP:
                        System.out.printf("| %-28s ", data.getTimestamp());
                        break;
                    default:
                        System.out.print("|");
                        System.out.print(" ".repeat(28));
                }
            }
            System.out.print("|\n");
        }
        System.out.println("+" + "-".repeat(columns.size() * 30 + 1) + "+");
    }

    private List<AuditLog> getAuditLogData(Condition condition) throws FileIOException {
        List<AuditLog> auditLogList = new ArrayList<>();
        for (String line : fileIOUtil.readFromFile(AppConstant.FilePaths.AUDIT_LOG_FILE_PATH)) {
            String[] logData = line.split(AppConstant.Delimiters.AUDIT_LOG_DELIMITER);
            if (condition == null ||
                    (condition.getKey().equals(AppConstant.AuditLogRows.TYPE) && logData[0].equals(condition.getValue())) ||
                    (condition.getKey().equals(AppConstant.AuditLogRows.USER_ID) && logData[1].equals(condition.getValue())) ||
                    (condition.getKey().equals(AppConstant.AuditLogRows.IP_ADDRESS) && logData[3].equals(condition.getValue()))
            ) {
                AuditLog auditLog = AuditLog
                        .builder()
                        .logType(logData[0])
                        .userId(logData[1])
                        .message(logData[2])
                        .ipAddress(logData[3])
                        .timestamp(logData[4])
                        .build();
                auditLogList.add(auditLog);
            }
        }
        return auditLogList;
    }


    private String generateIPAddress() {
        return "192.168.0" + ((int) (Math.random() * 255));
    }

    private String currentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
