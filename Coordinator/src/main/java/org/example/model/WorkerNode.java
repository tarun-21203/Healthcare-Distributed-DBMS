package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkerNode {
    private String workerId;
    private String host;
    private int port;
    private boolean isActive;
    private long lastHeartbeat;

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}
