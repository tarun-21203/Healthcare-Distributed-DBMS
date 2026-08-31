package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.WorkerCredentials;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WorkerCredentialsManager {
    private static WorkerCredentialsManager instance;
    private final Map<String, WorkerCredentials> workerCredentials;
    private final ObjectMapper objectMapper;
    private static final String CREDENTIALS_FILE = "data/worker-credentials.json";

    private WorkerCredentialsManager() {
        this.workerCredentials = new HashMap<>();
        this.objectMapper = new ObjectMapper();
        loadCredentials();
    }

    public static synchronized WorkerCredentialsManager getInstance() {
        if (instance == null) {
            instance = new WorkerCredentialsManager();
        }
        return instance;
    }

    private void loadCredentials() {
        System.out.println("[COORDINATOR-AUTH] 📂 Loading worker credentials from: " + CREDENTIALS_FILE);
        try {
            File credentialsFile = new File(CREDENTIALS_FILE);
            if (!credentialsFile.exists()) {
                System.err.println("[COORDINATOR-AUTH] ❌ Worker credentials file not found: " + CREDENTIALS_FILE);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(credentialsFile);
            JsonNode workersNode = rootNode.get("workers");

            if (workersNode != null && workersNode.isArray()) {
                for (JsonNode workerNode : workersNode) {
                    String workerId = workerNode.get("workerId").asText();
                    String username = workerNode.get("username").asText();
                    String password = workerNode.get("password").asText();
                    
                    WorkerCredentials credentials = new WorkerCredentials(workerId, username, password);
                    workerCredentials.put(workerId, credentials);
                    System.out.println("[COORDINATOR-AUTH] 👤 Loaded credentials for worker: " + workerId + " (user: " + username + ")");
                }
                System.out.println("[COORDINATOR-AUTH] ✅ Successfully loaded credentials for " + workerCredentials.size() + " workers");
            }
        } catch (IOException e) {
            System.err.println("[COORDINATOR-AUTH] ❌ Failed to load worker credentials: " + e.getMessage());
        }
    }

    public WorkerCredentials getCredentials(String workerId) {
        WorkerCredentials credentials = workerCredentials.get(workerId);
        if (credentials != null) {
            System.out.println("[COORDINATOR-AUTH] 🔍 Retrieved credentials for worker: " + workerId);
        } else {
            System.err.println("[COORDINATOR-AUTH] ❌ No credentials found for worker: " + workerId);
        }
        return credentials;
    }

    public boolean hasCredentials(String workerId) {
        return workerCredentials.containsKey(workerId);
    }

    public void reloadCredentials() {
        workerCredentials.clear();
        loadCredentials();
    }
}