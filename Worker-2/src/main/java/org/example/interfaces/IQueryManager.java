package org.example.interfaces;

import org.example.POJOs.SelectResponse;
import org.example.exception.FileIOException;

import java.io.IOException;

public interface IQueryManager {
    /**
     * Parse the query and then call the private methods based on it.
     *
     * @param queries
     * @throws FileIOException
     * @throws IOException
     */
    SelectResponse queryParser(String queries) throws FileIOException, IOException;
}
