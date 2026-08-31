package org.example.model;

public enum FragmentationType {
    NONE,           // No fragmentation - full table replication
    HORIZONTAL,     // Rows distributed across workers
    VERTICAL        // Columns distributed across workers
}
