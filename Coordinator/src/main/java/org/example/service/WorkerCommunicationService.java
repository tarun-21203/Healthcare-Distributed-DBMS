package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.POJOs.WorkerRequest;
import org.example.POJOs.WorkerResponse;
import org.example.model.WorkerNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

public class WorkerCommunicationService {
    private final ObjectMapper objectMapper;
    private final WorkerRegistry workerRegistry;
    private final WorkerAuthenticationManager authManager;
    private final ExecutorService executorService;
    private static final int TIMEOUT_SECONDS = 10;
    private String currentDatabase;

    public WorkerCommunicationService() {
        this.objectMapper = new ObjectMapper();
        this.workerRegistry = WorkerRegistry.getInstance();
        this.authManager = WorkerAuthenticationManager.getInstance();
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public WorkerResponse sendToWorker(WorkerNode worker, WorkerRequest request) {
        try {
            // Get authentication token
            System.out.println("[COORDINATOR-AUTH] Requesting auth token for worker: " + worker.getWorkerId());
            String authToken = authManager.getAuthToken(worker);
            if (authToken == null) {
                System.err.println("[COORDINATOR-AUTH] Failed to obtain auth token for worker: " + worker.getWorkerId());
                return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                        "Authentication failed - unable to get auth token");
            }
            System.out.println("[COORDINATOR-AUTH] Using auth token for request to worker: " + worker.getWorkerId());

            URL url = new URL(worker.getBaseUrl() + "/execute");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            conn.setReadTimeout(TIMEOUT_SECONDS * 1000);

            String jsonRequest = objectMapper.writeValueAsString(request);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    workerRegistry.updateHeartbeat(worker.getWorkerId());
                    return objectMapper.readValue(response.toString(), WorkerResponse.class);
                }
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Token expired, invalidate and retry once with fresh token
                System.err.println("[COORDINATOR-AUTH] Received 401 Unauthorized from worker: " + worker.getWorkerId());
                authManager.invalidateToken(worker.getWorkerId());
                System.out.println("[COORDINATOR-AUTH] Attempting to re-authenticate and retry...");
                
                // Try to get a fresh token and retry the request
                String freshToken = authManager.getAuthToken(worker);
                if (freshToken != null) {
                    System.out.println("[COORDINATOR-AUTH] Got fresh token, retrying request to worker: " + worker.getWorkerId());
                    return retryRequestWithFreshToken(worker, request, freshToken);
                } else {
                    System.err.println("[COORDINATOR-AUTH] Failed to get fresh token for worker: " + worker.getWorkerId());
                    return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                            "Authentication failed - unable to refresh token");
                }
            } else {
                workerRegistry.markInactive(worker.getWorkerId());
                return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                        "HTTP Error: " + responseCode);
            }
        } catch (Exception e) {
            workerRegistry.markInactive(worker.getWorkerId());
            return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                    "Connection failed: " + e.getMessage());
        }
    }

    public List<WorkerResponse> broadcastToAllWorkers(WorkerRequest request) {
        List<WorkerNode> activeWorkers = workerRegistry.getActiveWorkers();
        List<Future<WorkerResponse>> futures = new ArrayList<>();

        for (WorkerNode worker : activeWorkers) {
            Future<WorkerResponse> future = executorService.submit(() -> sendToWorker(worker, request));
            futures.add(future);
        }

        List<WorkerResponse> responses = new ArrayList<>();
        for (Future<WorkerResponse> future : futures) {
            try {
                responses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
                responses.add(createErrorResponse(request.getRequestId(), "unknown",
                        "Timeout or error: " + e.getMessage()));
            }
        }

        return responses;
    }

    public WorkerResponse sendToSpecificWorker(String workerId, WorkerRequest request) {
        WorkerNode worker = workerRegistry.getWorker(workerId);
        if (worker == null) {
            return createErrorResponse(request.getRequestId(), workerId, "Worker not found");
        }
        return sendToWorker(worker, request);
    }

    public boolean checkWorkerHealth(WorkerNode worker) {
        boolean wasActive = worker.isActive();
        try {
            URL url = new URL(worker.getBaseUrl() + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                workerRegistry.updateHeartbeat(worker.getWorkerId());
                
                // If worker just became active, sync current database
                if (!wasActive && currentDatabase != null) {
                    syncDatabaseToWorker(worker, currentDatabase);
                }
                return true;
            }
        } catch (Exception e) {
            workerRegistry.markInactive(worker.getWorkerId());
        }
        return false;
    }
    
    private void syncDatabaseToWorker(WorkerNode worker, String database) {
        try {
            WorkerRequest request = new WorkerRequest();
            request.setRequestId(UUID.randomUUID().toString());
            request.setQuery("USE " + database + ";");
            request.setOperation("USE");
            request.setDatabase(database);
            
            sendToWorker(worker, request);
            System.out.println("[SYNC] Synced database '" + database + "' to worker: " + worker.getWorkerId());
        } catch (Exception e) {
            System.out.println("[SYNC] Failed to sync database to worker " + worker.getWorkerId() + ": " + e.getMessage());
        }
    }

    public void startHealthCheckScheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            List<WorkerNode> workers = workerRegistry.getAllWorkers();
            for (WorkerNode worker : workers) {
                checkWorkerHealth(worker);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private WorkerResponse createErrorResponse(String requestId, String workerId, String message) {
        WorkerResponse response = new WorkerResponse();
        response.setRequestId(requestId);
        response.setWorkerId(workerId);
        response.setSuccess(false);
        response.setMessage(message);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    public void setCurrentDatabase(String database) {
        this.currentDatabase = database;
    }
    
    public String getCurrentDatabase() {
        return currentDatabase;
    }

    private WorkerResponse retryRequestWithFreshToken(WorkerNode worker, WorkerRequest request, String freshToken) {
        try {
            URL url = new URL(worker.getBaseUrl() + "/execute");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + freshToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            conn.setReadTimeout(TIMEOUT_SECONDS * 1000);

            String jsonRequest = objectMapper.writeValueAsString(request);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    workerRegistry.updateHeartbeat(worker.getWorkerId());
                    System.out.println("[COORDINATOR-AUTH] Retry successful for worker: " + worker.getWorkerId());
                    return objectMapper.readValue(response.toString(), WorkerResponse.class);
                }
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // Still getting 401 after fresh token - authentication problem
                System.err.println("[COORDINATOR-AUTH] Still receiving 401 after token refresh for worker: " + worker.getWorkerId());
                authManager.invalidateToken(worker.getWorkerId());
                return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                        "Authentication failed - persistent authentication issues");
            } else {
                workerRegistry.markInactive(worker.getWorkerId());
                return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                        "HTTP Error on retry: " + responseCode);
            }
        } catch (Exception e) {
            workerRegistry.markInactive(worker.getWorkerId());
            return createErrorResponse(request.getRequestId(), worker.getWorkerId(),
                    "Retry failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
