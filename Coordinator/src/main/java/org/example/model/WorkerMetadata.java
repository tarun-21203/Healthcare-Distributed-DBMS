package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkerMetadata {
    private String workerId;
    private List<String> databases;  // Databases on this worker
    private List<String> tables;     // Tables on this worker (format: "database.table")
    private long totalRecords;       // Total number of records
    private long storageUsed;        // Storage used in bytes
    
    public WorkerMetadata(String workerId) {
        this.workerId = workerId;
        this.databases = new ArrayList<>();
        this.tables = new ArrayList<>();
        this.totalRecords = 0;
        this.storageUsed = 0;
    }
    
    public void addTable(String database, String table) {
        String fullTableName = database + "." + table;
        if (!tables.contains(fullTableName)) {
            tables.add(fullTableName);
        }
        if (!databases.contains(database)) {
            databases.add(database);
        }
    }
    
    public void removeTable(String database, String table) {
        String fullTableName = database + "." + table;
        tables.remove(fullTableName);
    }
}
