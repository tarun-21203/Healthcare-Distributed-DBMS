package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.POJOs.AuthRequest;
import org.example.POJOs.AuthTokenResponse;
import org.example.model.WorkerCredentials;
import org.example.model.WorkerNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerAuthenticationManager {
    private static WorkerAuthenticationManager instance;
    private final Map<String, String> workerTokens;
    private final WorkerCredentialsManager credentialsManager;
    private final ObjectMapper objectMapper;
    private static final int AUTH_TIMEOUT_SECONDS = 5;

    private WorkerAuthenticationManager() {
        this.workerTokens = new ConcurrentHashMap<>();
        this.credentialsManager = WorkerCredentialsManager.getInstance();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized WorkerAuthenticationManager getInstance() {
        if (instance == null) {
            instance = new WorkerAuthenticationManager();
        }
        return instance;
    }

    public String getAuthToken(WorkerNode worker) {
        String workerId = worker.getWorkerId();
        
        // Check if we already have a valid token
        if (workerTokens.containsKey(workerId)) {
            System.out.println("[COORDINATOR-AUTH] Using cached token for worker: " + workerId);
            return workerTokens.get(workerId);
        }

        // Authenticate and get new token
        System.out.println("[COORDINATOR-AUTH] No cached token found, authenticating with worker: " + workerId);
        return authenticateWithWorker(worker);
    }

    private String authenticateWithWorker(WorkerNode worker) {
        String workerId = worker.getWorkerId();
        System.out.println("[COORDINATOR-AUTH] Starting authentication with worker: " + workerId + " at " + worker.getBaseUrl());
        
        try {
            WorkerCredentials credentials = credentialsManager.getCredentials(workerId);
            if (credentials == null) {
                System.err.println("[COORDINATOR-AUTH] No credentials found for worker: " + workerId);
                return null;
            }

            System.out.println("[COORDINATOR-AUTH] Found credentials for user: " + credentials.getUsername() + " on worker: " + workerId);

            URL url = new URL(worker.getBaseUrl() + "/authenticate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(AUTH_TIMEOUT_SECONDS * 1000);
            conn.setReadTimeout(AUTH_TIMEOUT_SECONDS * 1000);

            AuthRequest authRequest = new AuthRequest(credentials.getUsername(), credentials.getPassword());
            String jsonRequest = objectMapper.writeValueAsString(authRequest);

            System.out.println("[COORDINATOR-AUTH] Sending authentication request to worker: " + workerId);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[COORDINATOR-AUTH] Received response code: " + responseCode + " from worker: " + workerId);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    AuthTokenResponse authResponse = objectMapper.readValue(response.toString(), AuthTokenResponse.class);
                    if (authResponse.isSuccess() && authResponse.getToken() != null) {
                        workerTokens.put(workerId, authResponse.getToken());
                        System.out.println("[COORDINATOR-AUTH] Authentication successful with worker: " + workerId);
                        System.out.println("[COORDINATOR-AUTH] Token cached for future requests to worker: " + workerId);
                        return authResponse.getToken();
                    } else {
                        System.err.println("[COORDINATOR-AUTH] Authentication failed for worker " + workerId + ": " + authResponse.getMessage());
                    }
                }
            } else {
                System.err.println("[COORDINATOR-AUTH] HTTP error " + responseCode + " when authenticating with worker: " + workerId);
            }
        } catch (Exception e) {
            System.err.println("[COORDINATOR-AUTH] Exception during authentication with worker " + workerId + ": " + e.getMessage());
        }

        return null;
    }

    public void invalidateToken(String workerId) {
        workerTokens.remove(workerId);
        System.out.println("[COORDINATOR-AUTH] Invalidated token for worker: " + workerId);
    }

    public void refreshToken(WorkerNode worker) {
        System.out.println("[COORDINATOR-AUTH] Refreshing token for worker: " + worker.getWorkerId());
        invalidateToken(worker.getWorkerId());
        authenticateWithWorker(worker);
    }

    public boolean hasValidToken(String workerId) {
        return workerTokens.containsKey(workerId);
    }
}