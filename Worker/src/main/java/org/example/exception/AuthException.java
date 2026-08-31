package org.example.exception;

public class AuthException extends Exception{
    /**
     * Constructs a new AuthException with the specified detail message
     * @param message the detail message
     */
    public AuthException(String message) {
        super(message);
    }

}
