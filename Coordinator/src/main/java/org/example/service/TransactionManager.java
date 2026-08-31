package org.example.service;

import org.example.POJOs.WorkerRequest;
import org.example.POJOs.WorkerResponse;
import org.example.model.WorkerNode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionManager {
    
    private final WorkerCommunicationService communicationService;
    private final QueryParser queryParser;
    
    public TransactionManager(WorkerCommunicationService communicationService) {
        this.communicationService = communicationService;
        this.queryParser = new QueryParser();
    }
    
    public void rollback(String originalQuery, String operation, 
                        List<WorkerNode> successfulWorkers, 
                        String requestId, String currentDatabase) {
        System.out.println("[ROLLBACK] Rolling back changes on " + successfulWorkers.size() + " worker(s)");
        
        String rollbackQuery = generateRollbackQuery(originalQuery, operation);
        
        if (rollbackQuery == null) {
            System.out.println("[ROLLBACK] Cannot generate rollback query for operation: " + operation);
            System.out.println("[ROLLBACK] Manual intervention may be required on workers: " + 
                    successfulWorkers.stream().map(WorkerNode::getWorkerId).toList());
            return;
        }
        
        System.out.println("[ROLLBACK] Rollback query: " + rollbackQuery);
        
        WorkerRequest rollbackRequest = new WorkerRequest();
        rollbackRequest.setRequestId(requestId + "-rollback");
        rollbackRequest.setQuery(rollbackQuery);
        rollbackRequest.setOperation(queryParser.determineOperation(rollbackQuery));
        rollbackRequest.setDatabase(currentDatabase);
        
        int rollbackSuccessCount = 0;
        int rollbackFailCount = 0;
        
        for (WorkerNode worker : successfulWorkers) {
            System.out.println("[ROLLBACK] Rolling back on worker: " + worker.getWorkerId());
            WorkerResponse response = communicationService.sendToWorker(worker, rollbackRequest);
            
            if (response.isSuccess()) {
                System.out.println("[ROLLBACK] Successfully rolled back on worker: " + worker.getWorkerId());
                rollbackSuccessCount++;
            } else {
                System.out.println("[ROLLBACK] Failed to rollback on worker: " + worker.getWorkerId());
                System.out.println("[ROLLBACK] Error: " + response.getMessage());
                rollbackFailCount++;
            }
        }
        
        System.out.println("\n[ROLLBACK] Summary:");
        System.out.println("  Rollback Successful: " + rollbackSuccessCount + " worker(s)");
        System.out.println("  Rollback Failed: " + rollbackFailCount + " worker(s)");
        
        if (rollbackFailCount > 0) {
            System.out.println("\n[WARNING] Some rollbacks failed - database may be in inconsistent state!");
            System.out.println("[WARNING] Manual intervention required on failed workers");
        } else {
            System.out.println("\n[TRANSACTION] Rollback completed successfully - transaction aborted");
        }
    }
    
    private String generateRollbackQuery(String originalQuery, String operation) {
        switch (operation) {
            case "INSERT":
                return generateDeleteFromInsert(originalQuery);
                
            case "UPDATE":
                System.out.println("[ROLLBACK] UPDATE rollback requires storing previous values (not yet implemented)");
                return null;
                
            case "DELETE":
                System.out.println("[ROLLBACK] DELETE rollback requires storing deleted rows (not yet implemented)");
                return null;
                
            case "CREATE":
                String tableName = queryParser.extractTableNameFromCreate(originalQuery);
                if (tableName != null) {
                    return "DROP TABLE " + tableName + ";";
                }
                return null;
                
            case "DROP":
                System.out.println("[ROLLBACK] DROP TABLE cannot be rolled back");
                return null;
                
            default:
                return null;
        }
    }
    
    private String generateDeleteFromInsert(String insertQuery) {
        String tableName = queryParser.extractTableName(insertQuery);
        if (tableName == null) {
            return null;
        }
        
        String[] values = queryParser.extractInsertValues(insertQuery);
        
        if (values.length == 0) {
            return null;
        }
        
        String idValue = values[0].trim().replace("'", "");
        
        return "DELETE FROM " + tableName + " WHERE id = " + idValue + ";";
    }
}
