package org.example.util.InputReaderUtility;

import org.example.exception.InputReaderException;

public interface IInputReaderUtil {
    /**
     * Open the input reader
     */
    void open();

    /**
     * Close the input reader
     */
    void close() throws InputReaderException;

    /**
     * Read a line of input
     * @return input string, null if no input available
     */
    String readLine() throws InputReaderException;
    
    /**
     * Read input with a message
     * @param message to display before reading
     * @return input string
     */
    String readLine(String message) throws InputReaderException;
    
    /**
     * Read an integer value
     * @return integer value
     */
    int readInt() throws InputReaderException;
    
    /**
     * Read an integer with message
     * @param message to display
     * @return integer value
     */
    int readInt(String message) throws InputReaderException;
    
    /**
     * Read a double value
     * @return double value
     */
    double readDouble() throws InputReaderException;
    
    /**
     * Read a double with message
     * @param message to display
     * @return double value
     */
    double readDouble(String message) throws InputReaderException;

}
