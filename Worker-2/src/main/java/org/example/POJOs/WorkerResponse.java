package org.example.POJOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkerResponse {
    private String requestId;
    private String workerId;
    private boolean success;
    private String message;
    private Object data;
    private long timestamp;
}
