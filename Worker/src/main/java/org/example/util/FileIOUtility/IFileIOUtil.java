package org.example.util.FileIOUtility;

import org.example.exception.FileIOException;

import java.util.List;

public interface IFileIOUtil {

    /**
     * Check if  the file exits at the path
     *
     * @param filepath
     * @return
     */

    public boolean checkFileExists(String filepath);

    /**
     * Creates a new file at the specified path.
     *
     * @param filePath
     * @throws FileIOException If an error occurs during the file creation process.
     */
    void createFile(String filePath) throws FileIOException;

    /**
     * Writes data to a file at the specified path.
     *
     * @param data The data to be written to the file.
     * @throws FileIOException If an error occurs during the file writing process.
     */
    void writeToFile(String filePath, List<String> data) throws FileIOException;

    /**
     * Appends data to a file at the specified path.
     *
     * @param data The data to be appended to the file.
     * @throws FileIOException If an error occurs during the file appending process.
     */
    void appendToFile(String filePath, String data) throws FileIOException;

    /**
     * Reads data from a file at the specified path.
     *
     * @return A list of strings representing the lines read from the file.
     * @throws FileIOException If an error occurs during the file reading process.
     */
    List<String> readFromFile(String filePath) throws FileIOException;

}
