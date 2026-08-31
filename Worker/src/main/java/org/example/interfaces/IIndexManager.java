package org.example.interfaces;

import org.example.exception.FileIOException;
import org.example.model.Condition;
import org.example.model.Table;
import org.example.model.Type;

import java.util.List;
import java.util.Map;

public interface IIndexManager {
    /**
     * Update the storage based on indexes
     *
     * @param tableMeta
     * @throws FileIOException
     */
    void refreshStorage(Map<String, Table> tableMeta) throws FileIOException;

    /**
     * update the indexes based on storage
     *
     * @param tableMeta
     * @throws FileIOException
     */
    void fetchIndexes(Map<String, Table> tableMeta) throws FileIOException;

    /**
     * Create an new index
     *
     * @param name index name
     */
    void createIndexes(String name);

    /**
     * Update the index
     *
     * @param tableName
     * @param index
     * @param column
     * @param value
     * @param tableMeta
     * @param isTransaction
     * @throws FileIOException
     */
    void updateToIndex(String tableName, Integer index, String column, String value, Map<String, Table> tableMeta, Boolean isTransaction) throws FileIOException;

    /**
     * insert in the index
     *
     * @param tableName
     * @param index
     * @param columns
     * @param tableMeta
     */
    void insertToIndex(String tableName, Integer index, List<String> columns, Map<String, Table> tableMeta);

    /**
     * delete an item in index
     *
     * @param tableName
     * @param index
     * @param tableMeta
     * @param isTransaction
     * @throws FileIOException
     */
    void deleteToIndex(String tableName, Integer index, Map<String, Table> tableMeta, Boolean isTransaction) throws FileIOException;

    /**
     * searching in an index
     *
     * @param tableName
     * @param index
     * @return
     */
    Map<String, Object> searchToIndex(String tableName, Integer index);

    /**
     * retrieve all data from index
     *
     * @param tableName
     * @return
     */
    List<Map<String, Object>> getAllData(String tableName);

    /**
     * Store a data in a file
     *
     * @param tableName
     * @param data
     * @throws FileIOException
     */
    void storeDataToFile(String tableName, List<String> data) throws FileIOException;

    /**
     *  search a file when index not present
     *
     * @param tableName
     * @param condition
     * @param columnNumber
     * @return
     * @throws FileIOException
     */
    int searchIndexInFile(String tableName, Condition condition, Integer columnNumber) throws FileIOException;

    /**
     * validate data type of table
     *
     * @param value
     * @param type
     */
    void validateDataType(String value, Type type);

    List<Map<String, Object>> searchAllIndexes(String name, Condition whereCondition);
}
