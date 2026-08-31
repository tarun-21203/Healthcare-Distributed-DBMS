package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Table {
    private String name;
    private List<Column> columns;
    private Integer totalRowCount = 0;

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
        this.totalRowCount = 0;
    }

    public void addColumn(Column column) {
        this.columns.add(column);
    }

    public void incrementTotalRowCount() {
        totalRowCount += 1;
    }

    public void decrementTotalRowCount() {
        totalRowCount -= 1;
    }
}
