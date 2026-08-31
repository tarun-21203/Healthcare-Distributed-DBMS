package org.example.interfaces;

import org.example.constant.AppConstant;
import org.example.exception.FileIOException;

public interface IAuditLogger {
    /**
     * Logs an audit message.
     *
     * @param userId  The user performing the action.
     * @param message The audit message to be logged.
     * @param logType The type of audit log i.e. LOGIN_SUCCESS, LOGIN_FAILURE etc.
     */
    void logAudit(String userId, String message, AppConstant.AUDIT_LOG_TYPE logType) throws FileIOException;

    /**
     * Getting last successful login
     *
     * @param userId
     * @return
     * @throws FileIOException
     */
    String lastSuccessfulLogin(String userId) throws FileIOException;

    /**
     * Getting the log data
     *
     * @param query
     * @throws FileIOException
     */
    public void getLogData(String query) throws FileIOException;
}

