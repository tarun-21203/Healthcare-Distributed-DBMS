package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
public class DistributedMetadata {
    private Map<String, WorkerMetadata> workers;  // workerId -> WorkerMetadata
    private Map<String, TableMetadata> tables;    // "database.table" -> TableMetadata
    private long lastUpdated;
    
    public DistributedMetadata() {
        this.workers = new HashMap<>();
        this.tables = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void addWorker(String workerId) {
        if (!workers.containsKey(workerId)) {
            workers.put(workerId, new WorkerMetadata(workerId));
        }
    }
    
    public void addTable(String database, String tableName, TableMetadata metadata) {
        String fullTableName = database + "." + tableName;
        tables.put(fullTableName, metadata);
        
        // Update worker metadata
        for (String workerId : metadata.getWorkerIds()) {
            WorkerMetadata workerMeta = workers.get(workerId);
            if (workerMeta != null) {
                workerMeta.addTable(database, tableName);
            }
        }
        
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public TableMetadata getTableMetadata(String database, String tableName) {
        String fullTableName = database + "." + tableName;
        return tables.get(fullTableName);
    }
    
    public void updateLastModified() {
        this.lastUpdated = System.currentTimeMillis();
    }
}
