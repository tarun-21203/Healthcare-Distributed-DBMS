package org.example.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Database {
    private Map<String, Table> tables;

    public Database() {
        tables = new HashMap<>();
    }

    public void addTable(Table table) {
        tables.put(table.getName(), table);
    }
}
