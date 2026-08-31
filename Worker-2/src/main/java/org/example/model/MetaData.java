package org.example.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class MetaData {
    private Map<String, Database> database;

    public MetaData() {
        this.database = new HashMap<>();
    }

    public void addDatabase(String dbName) {
        if (database.isEmpty()) {
            database.put(dbName, new Database());
        } else {
            throw new IllegalArgumentException("Database Already Exists");
        }
    }
}
