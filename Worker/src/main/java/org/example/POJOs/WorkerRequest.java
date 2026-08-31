package org.example.POJOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkerRequest {
    private String requestId;
    private String operation;
    private String query;
    private String database;
    private String table;
    private Object data;
}
