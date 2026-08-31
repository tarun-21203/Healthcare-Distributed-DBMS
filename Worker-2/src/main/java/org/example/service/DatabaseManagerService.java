package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.POJOs.SelectResponse;
import org.example.constant.AppConstant;
import org.example.exception.FileIOException;
import org.example.interfaces.IDatabaseManager;
import org.example.interfaces.IIndexManager;
import org.example.model.Column;
import org.example.model.Condition;
import org.example.model.MetaData;
import org.example.model.Table;
import org.example.util.FileIOUtility.TextFileIOUtil;

import java.util.*;

public class DatabaseManagerService implements IDatabaseManager {

    private String currentDatabase;
    private MetaData metaData;
    private final ObjectMapper objectMapper;
    private final IIndexManager indexManagerService;
    private final TextFileIOUtil fileIOUtil;

    private Boolean isTransaction;
    private IIndexManager transactionManagerService;
    private String lockedUser;
    private final Queue<String> lockQueue;

    public DatabaseManagerService() throws FileIOException, JsonProcessingException {
        objectMapper = new ObjectMapper();
        fileIOUtil = new TextFileIOUtil();
        indexManagerService = new IndexManagerService();
        transactionManagerService = new IndexManagerService();
        isTransaction = false;
        lockQueue = new LinkedList<>();
        fetchMetaData();
    }

    private void fetchMetaData() throws FileIOException, JsonProcessingException {
        fileIOUtil.createFile(AppConstant.FilePaths.META_DATA_FILE_PATH);
        String data = fileIOUtil.readFileAsString(AppConstant.FilePaths.META_DATA_FILE_PATH);
        if (data == null || data.isEmpty()) {
            metaData = new MetaData();
        } else {
            metaData = objectMapper.readValue(data, MetaData.class);
        }
    }

    private void storeMetaData() throws FileIOException, JsonProcessingException {
        if (!isTransaction) {
            fileIOUtil.writeFileAsString(AppConstant.FilePaths.META_DATA_FILE_PATH, objectMapper.writeValueAsString(metaData));
        }
    }

    private void checkActiveDatabase() {
        if (currentDatabase == null) {
            throw new IllegalArgumentException("No Database Selected");
        }
    }

    private void checkActiveUserTransaction(String user) {
        if (isTransaction && !lockedUser.equals(user)) {
            throw new IllegalArgumentException("Transaction can't proceed due to locking");
        }
    }

    private void checkActiveTransaction() {
        if (!isTransaction) {
            throw new IllegalArgumentException("No active transaction");
        }
    }

    private IIndexManager getIndexForTransaction() {
        if (isTransaction)
            return transactionManagerService;
        else
            return indexManagerService;
    }

    @Override
    public void createDatabase(String name) throws FileIOException, JsonProcessingException {
        if (metaData.getDatabase().isEmpty()) {
            metaData.addDatabase(name);
            storeMetaData();
        } else {
            throw new IllegalArgumentException("Database already exists");
        }
    }

    @Override
    public List<String> showDatabase() {
        return new ArrayList<>(metaData.getDatabase().keySet());
    }

    @Override
    public void useDatabase(String name) throws FileIOException {
        if (metaData.getDatabase().containsKey(name)) {
            currentDatabase = name;
            Map<String, Table> tableMeta = metaData.getDatabase().get(currentDatabase).getTables();
            indexManagerService.fetchIndexes(tableMeta);
        } else {
            throw new IllegalArgumentException("Database Not Found");
        }
    }

    @Override
    public void createTable(String name, List<Column> columns) throws FileIOException, JsonProcessingException {
        checkActiveDatabase();
        if (isTransaction) {
            throw new IllegalArgumentException("Create Table not supported");
        }
        IIndexManager selectedManager = getIndexForTransaction();
        if (metaData.getDatabase().get(currentDatabase).getTables().containsKey(name)) {
            throw new IllegalArgumentException("Table Already Exists");
        }
        metaData.getDatabase().get(currentDatabase).addTable(new Table(name, columns));
        selectedManager.createIndexes(name);

        if (!isTransaction) {
            fileIOUtil.createFile(AppConstant.FilePaths.DATA_FILE_PATH + name + AppConstant.FileType.TXT);
        }
        storeMetaData();
    }

    private void checkTableExists(String tableName) {
        System.out.println(metaData.getDatabase().get(currentDatabase).getTables().keySet());
        System.out.println(tableName);
        if (!metaData.getDatabase().get(currentDatabase).getTables().containsKey(tableName))
            throw new IllegalArgumentException("Table Not Exists");
    }

    @Override
    public void insertRow(String name, List<String> columns, String user) throws FileIOException {
        checkActiveDatabase();
        checkActiveUserTransaction(user);
        checkTableExists(name);
        IIndexManager selectedManager = getIndexForTransaction();
        Table table = metaData.getDatabase().get(currentDatabase).getTables().get(name);
        List<Column> columnsMeta = table.getColumns();
        if (columnsMeta.size() != columns.size()) {
            throw new IllegalArgumentException(String.format("Column Mismatch: expect %d values but got %d", columnsMeta.size(), columns.size()));
        }
        selectedManager.insertToIndex(name, Integer.parseInt(columns.get(0)), columns, metaData.getDatabase().get(currentDatabase).getTables());
        table.incrementTotalRowCount();
        if (!isTransaction) {
            indexManagerService.storeDataToFile(name, columns);
        }
    }

    @Override
    public void updateColumn(String name, Condition setCondition, Condition whereCondition, String user) throws FileIOException {
        checkActiveDatabase();
        checkActiveUserTransaction(user);
        checkTableExists(name);
        List<Column> columns = metaData.getDatabase().get(currentDatabase).getTables().get(name).getColumns();
        IIndexManager selectedManager = getIndexForTransaction();

        int index = -1;
        if (!columns.get(0).getName().equals(whereCondition.getKey())) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getName().equals(whereCondition.getKey())) {
                    selectedManager.validateDataType(whereCondition.getValue(), columns.get(i).getType());
                    index = selectedManager.searchIndexInFile(name, whereCondition, i);
                }
            }
            if (index == -1) {
                throw new IllegalArgumentException("No Data Found");
            }
        } else {
            selectedManager.validateDataType(whereCondition.getValue(), columns.get(0).getType());
            index = Integer.parseInt(whereCondition.getValue());
        }

        Column setColumn = columns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(setCondition.getKey()))
                .findFirst()
                .orElse(null);

        if (setColumn == null) {
            throw new IllegalArgumentException("No set column found");
        }
        selectedManager.updateToIndex(name,
                index,
                setColumn.getName(),
                setCondition.getValue().trim().replace("'", ""),
                metaData.getDatabase().get(currentDatabase).getTables(),
                isTransaction
        );
    }

    @Override
    public SelectResponse searchRow(String name, List<String> columns, Condition whereCondition, String user) throws FileIOException {
        checkActiveDatabase();
        checkTableExists(name);
        List<Column> columnMeta = metaData.getDatabase().get(currentDatabase).getTables().get(name).getColumns();
        IIndexManager selectedManager;
        if (isTransaction && lockedUser.equals(user))
            selectedManager = transactionManagerService;

        else
            selectedManager = indexManagerService;

        if (columns.size() == 1 && columns.get(0).equals("*")) {
            columns = columnMeta.stream().map(Column::getName).toList();
        } else {
            for (String columnName : columns) {
                Column setColumn = columnMeta.stream()
                        .filter(c -> c.getName().equalsIgnoreCase(columnName))
                        .findFirst()
                        .orElse(null);

                if (setColumn == null) {
                    throw new IllegalArgumentException(columnName + " column not found");
                }
            }
        }

        if (whereCondition == null) {
            return new SelectResponse(columns, selectedManager.getAllData(name));
        } else {
            Integer index = -1;
            if (!columnMeta.get(0).getName().equals(whereCondition.getKey())) {
                for (int i = 0; i < columnMeta.size(); i++) {
                    if (columnMeta.get(i).getName().equals(whereCondition.getKey())) {
                        selectedManager.validateDataType(whereCondition.getValue(), columnMeta.get(i).getType());
                        index = selectedManager.searchIndexInFile(name, whereCondition, i);
                    }
                }
                if (index == -1) {
                    throw new IllegalArgumentException("No Data Found");
                }
            } else {
                selectedManager.validateDataType(whereCondition.getValue(), columnMeta.get(0).getType());
                index = Integer.parseInt(whereCondition.getValue());
            }
            return new SelectResponse(columns, selectedManager.searchAllIndexes(name, whereCondition));
        }
    }

    @Override
    public void deleteRow(String name, Condition whereCondition, String user) throws FileIOException {
        checkActiveDatabase();
        checkActiveUserTransaction(user);
        checkTableExists(name);

        List<Column> columns = metaData.getDatabase().get(currentDatabase).getTables().get(name).getColumns();
        IIndexManager selectedManager = getIndexForTransaction();
        int index = -1;
        if (!columns.get(0).getName().equals(whereCondition.getKey())) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getName().equals(whereCondition.getKey())) {
                    selectedManager.validateDataType(whereCondition.getValue(), columns.get(i).getType());
                    index = selectedManager.searchIndexInFile(name, whereCondition, i);
                }
            }
            if (index == -1) {
                throw new IllegalArgumentException("No Data Found");
            }
        } else {
            selectedManager.validateDataType(whereCondition.getValue(), columns.get(0).getType());
            index = Integer.parseInt(whereCondition.getValue());
        }

        selectedManager.deleteToIndex(name,
                index,
                metaData.getDatabase().get(currentDatabase).getTables(),
                isTransaction
        );
    }

    @Override
    public void startTransaction(String user) throws FileIOException {
        checkActiveDatabase();
        if (isTransaction) {
            if (user.equals(lockedUser)) {
                throw new IllegalArgumentException("Transaction already in progress");
            } else {
                lockQueue.add(user);
                System.out.println("Transaction waiting for write lock");
            }
        } else {
            Map<String, Table> tableMeta = metaData.getDatabase().get(currentDatabase).getTables();
            transactionManagerService.fetchIndexes(tableMeta);
            lockedUser = user;
            isTransaction = true;
            System.out.println("Transaction started");
        }
    }

    @Override
    public void commitTransaction(String user) throws FileIOException {
        if (user.equals(lockedUser)) {
            checkActiveTransaction();
            Map<String, Table> tableMeta = metaData.getDatabase().get(currentDatabase).getTables();
            transactionManagerService.refreshStorage(tableMeta);
            indexManagerService.fetchIndexes(tableMeta);
            if (lockQueue.isEmpty()) {
                isTransaction = false;
            } else {
                lockedUser = lockQueue.poll();
            }
            System.out.println("Transaction Commited");
        } else {
            throw new IllegalArgumentException("Transaction can't proceed due to locking");
        }
    }

    @Override
    public void rollbackTransaction(String user) {
        if (user.equals(lockedUser)) {
            checkActiveTransaction();
            transactionManagerService = new IndexManagerService();
            if (lockQueue.isEmpty()) {
                isTransaction = false;
            } else {
                lockedUser = lockQueue.poll();
            }
            System.out.println("Transaction Rollback successfully");
        } else {
            throw new IllegalArgumentException("Transaction can't proceed due to locking");
        }
    }

    @Override
    public List<Column> describeTable(String name) {
        checkActiveDatabase();
        if (metaData.getDatabase().get(currentDatabase).getTables().containsKey(name)) {
            return metaData.getDatabase().get(currentDatabase).getTables().get(name).getColumns();
        } else {
            throw new IllegalArgumentException("Table Not Found");
        }
    }

}
