package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.model.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MetadataManager {
    private static MetadataManager instance;
    private final String metadataFilePath;
    private DistributedMetadata metadata;
    private final ObjectMapper objectMapper;
    
    private MetadataManager() {
        this.metadataFilePath = "data/db/metadata.json";
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        loadMetadata();
    }
    
    public static synchronized MetadataManager getInstance() {
        if (instance == null) {
            instance = new MetadataManager();
        }
        return instance;
    }
    
    private void loadMetadata() {
        File file = new File(metadataFilePath);
        if (file.exists()) {
            try {
                metadata = objectMapper.readValue(file, DistributedMetadata.class);
                System.out.println("Loaded metadata from " + metadataFilePath);
            } catch (IOException e) {
                System.out.println("Failed to load metadata, creating new: " + e.getMessage());
                metadata = new DistributedMetadata();
            }
        } else {
            metadata = new DistributedMetadata();
            System.out.println("No existing metadata found, created new metadata");
        }
    }
    
    public void saveMetadata() {
        try {
            File file = new File(metadataFilePath);
            file.getParentFile().mkdirs();
            metadata.updateLastModified();
            objectMapper.writeValue(file, metadata);
            System.out.println("Metadata saved to " + metadataFilePath);
        } catch (IOException e) {
            System.err.println("Failed to save metadata: " + e.getMessage());
        }
    }
    
    public void registerWorker(String workerId) {
        metadata.addWorker(workerId);
        saveMetadata();
    }
    
    public void registerTable(String database, String tableName, List<String> workerIds, 
                              FragmentationType fragmentationType, boolean isReplicated) {
        TableMetadata tableMeta = new TableMetadata();
        tableMeta.setTableName(tableName);
        tableMeta.setDatabase(database);
        tableMeta.setFragmentationType(fragmentationType);
        tableMeta.setWorkerIds(workerIds);
        tableMeta.setReplicated(isReplicated);
        tableMeta.setReplicationFactor(isReplicated ? workerIds.size() : 1);
        tableMeta.setFragmentationDetails(new HashMap<>());
        
        metadata.addTable(database, tableName, tableMeta);
        saveMetadata();
    }
    
    public void registerHorizontalFragmentation(String database, String tableName, 
                                                String fragmentationColumn,
                                                Map<String, String> fragmentRanges) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        if (tableMeta != null) {
            tableMeta.setFragmentationType(FragmentationType.HORIZONTAL);
            tableMeta.setFragmentationColumn(fragmentationColumn);
            tableMeta.setFragmentRanges(fragmentRanges);
            tableMeta.setReplicated(false);
            saveMetadata();
        }
    }
    
    public void registerVerticalFragmentation(String database, String tableName,
                                              Map<String, List<String>> verticalFragments) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        if (tableMeta != null) {
            tableMeta.setFragmentationType(FragmentationType.VERTICAL);
            tableMeta.setVerticalFragments(verticalFragments);
            tableMeta.setReplicated(false);
            saveMetadata();
        }
    }
    
    public void registerVerticalFragmentation(String database, String tableName,
                                              Map<String, List<String>> verticalFragments,
                                              List<String> allColumns) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        if (tableMeta != null) {
            tableMeta.setFragmentationType(FragmentationType.VERTICAL);
            tableMeta.setVerticalFragments(verticalFragments);
            tableMeta.setAllColumns(allColumns);
            tableMeta.setReplicated(false);
            saveMetadata();
        }
    }
    
    public List<String> getWorkersForTable(String database, String tableName) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        return tableMeta != null ? tableMeta.getWorkerIds() : new ArrayList<>();
    }
    
    public List<String> getWorkersForQuery(String database, String tableName, String whereClause) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        if (tableMeta == null) {
            return new ArrayList<>();
        }
        
        // If replicated, can query any worker
        if (tableMeta.isReplicated()) {
            return tableMeta.getWorkerIds();
        }
        
        // For horizontal fragmentation, determine which workers have relevant data
        if (tableMeta.getFragmentationType() == FragmentationType.HORIZONTAL) {
            // TODO: Parse WHERE clause and determine relevant workers
            // For now, return all workers
            return tableMeta.getWorkerIds();
        }
        
        // For vertical fragmentation, need all workers to reconstruct full row
        if (tableMeta.getFragmentationType() == FragmentationType.VERTICAL) {
            return tableMeta.getWorkerIds();
        }
        
        return tableMeta.getWorkerIds();
    }
    
    public FragmentationType getFragmentationType(String database, String tableName) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        return tableMeta != null ? tableMeta.getFragmentationType() : FragmentationType.NONE;
    }
    
    public boolean isTableReplicated(String database, String tableName) {
        TableMetadata tableMeta = metadata.getTableMetadata(database, tableName);
        return tableMeta != null && tableMeta.isReplicated();
    }
    
    public DistributedMetadata getMetadata() {
        return metadata;
    }
    
    public void displayMetadata() {
        System.out.println("\n=== Distributed Metadata ===");
        System.out.println("Last Updated: " + new Date(metadata.getLastUpdated()));
        
        System.out.println("\n--- Workers ---");
        for (Map.Entry<String, WorkerMetadata> entry : metadata.getWorkers().entrySet()) {
            WorkerMetadata worker = entry.getValue();
            System.out.println("\nWorker: " + worker.getWorkerId());
            System.out.println("  Databases: " + worker.getDatabases());
            System.out.println("  Tables: " + worker.getTables());
            System.out.println("  Total Records: " + worker.getTotalRecords());
        }
        
        System.out.println("\n--- Tables ---");
        for (Map.Entry<String, TableMetadata> entry : metadata.getTables().entrySet()) {
            TableMetadata table = entry.getValue();
            System.out.println("\nTable: " + entry.getKey());
            System.out.println("  Fragmentation: " + table.getFragmentationType());
            System.out.println("  Replicated: " + table.isReplicated());
            System.out.println("  Workers: " + table.getWorkerIds());
            
            if (table.getFragmentationType() == FragmentationType.HORIZONTAL) {
                System.out.println("  Fragmentation Column: " + table.getFragmentationColumn());
                System.out.println("  Fragment Ranges: " + table.getFragmentRanges());
            }
            
            if (table.getFragmentationType() == FragmentationType.VERTICAL) {
                System.out.println("  Vertical Fragments: " + table.getVerticalFragments());
            }
        }
        System.out.println();
    }
    
    public void removeTable(String database, String tableName) {
        String fullTableName = database + "." + tableName;
        TableMetadata tableMeta = metadata.getTables().remove(fullTableName);
        
        if (tableMeta != null) {
            // Update worker metadata
            for (String workerId : tableMeta.getWorkerIds()) {
                WorkerMetadata workerMeta = metadata.getWorkers().get(workerId);
                if (workerMeta != null) {
                    workerMeta.removeTable(database, tableName);
                }
            }
            saveMetadata();
        }
    }
}
