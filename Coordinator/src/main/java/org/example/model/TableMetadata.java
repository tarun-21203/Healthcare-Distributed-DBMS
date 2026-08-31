package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableMetadata {
    private String tableName;
    private String database;
    private FragmentationType fragmentationType;
    private List<String> workerIds;
    private Map<String, Object> fragmentationDetails;
    private boolean isReplicated;
    private int replicationFactor;

    private String fragmentationColumn;  // Column used for horizontal partitioning
    private Map<String, String> fragmentRanges;  // workerId -> range (e.g., "id >= 1 AND id <= 1000")
    
    // For vertical fragmentation
    private Map<String, List<String>> verticalFragments;  // workerId -> list of columns
    private List<String> allColumns;  // Original column order from CREATE TABLE
}
