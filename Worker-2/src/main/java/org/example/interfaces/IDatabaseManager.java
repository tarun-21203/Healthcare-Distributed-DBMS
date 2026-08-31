package org.example.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.POJOs.SelectResponse;
import org.example.exception.FileIOException;
import org.example.model.Column;
import org.example.model.Condition;

import java.util.List;

public interface IDatabaseManager {
    /**
     * create a new database
     *
     * @param name database name
     * @throws FileIOException
     * @throws JsonProcessingException
     */
    void createDatabase(String name) throws FileIOException, JsonProcessingException;

    /**
     * return all existing database names
     *
     * @return
     */
    List<String> showDatabase();

    /**
     * Use a database -> loading all the indexes for that DB when use is called
     *
     * @param name tableName
     * @throws FileIOException
     */
    void useDatabase(String name) throws FileIOException;

    /**
     * Create DB table -> create an empty file and updating metadata
     *
     * @param name tableName
     * @param columns
     * @throws FileIOException
     * @throws JsonProcessingException
     */
    void createTable(String name, List<Column> columns) throws FileIOException, JsonProcessingException;

    /**
     * Database insertion -> Insertion in file and in index
     * @param name  tableName
     * @param columns
     * @param user
     * @throws FileIOException
     */
    void insertRow(String name, List<String> columns, String user) throws FileIOException;

    /**
     * Update Column -> update both in memory index and file storage
     *
     * @param name tableName
     * @param setCondition
     * @param whereCondition
     * @param user
     * @throws FileIOException
     */
    void updateColumn(String name, Condition setCondition, Condition whereCondition, String user) throws FileIOException;

    /**
     * Search a record -> search on index
     *
     * @param name tableName
     * @param columns
     * @param whereCondition
     * @param user
     * @return
     * @throws FileIOException
     */
    SelectResponse searchRow(String name, List<String> columns, Condition whereCondition, String user) throws FileIOException;

    /**
     * Delete a record -> delete from index and file too
     * @param name tableName
     * @param whereCondition
     * @param user
     * @throws FileIOException
     */
    void deleteRow(String name, Condition whereCondition, String user) throws FileIOException;

    /**
     * Start a transaction -> if no transaction then start the transaction instantly otherwise put it in queue
     *
     * @param user
     * @throws FileIOException
     * @throws JsonProcessingException
     */
    void startTransaction(String user) throws FileIOException, JsonProcessingException;

    /**
     * Commit a transaction -> Update the index and file storage and check the queue if there is transaction than give him the write locks
     *
     * @param user
     * @throws FileIOException
     * @throws JsonProcessingException
     */
    void commitTransaction(String user) throws FileIOException, JsonProcessingException;

    /**
     * Rollback a transaction -> emptying the transaction index and check the queue if there is transaction than give him the write locks
     *
     * @param user
     * @throws FileIOException
     */
    void rollbackTransaction(String user) throws FileIOException;

    /**
     * Return table attributes from metadata
     *
     * @param name tableName
     * @return
     */
    List<Column> describeTable(String name);
}
