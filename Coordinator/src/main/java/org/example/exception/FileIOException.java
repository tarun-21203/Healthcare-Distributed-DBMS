package org.example.exception;

public class FileIOException extends Exception{
    /**
     * Constructs a new FileWritingException with the specified detail message
     * @param message the detail message
     */
    public FileIOException(String message) {
        super(message);
    }

}
