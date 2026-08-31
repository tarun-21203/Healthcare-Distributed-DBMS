package org.example.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.POJOs.AuthRequest;
import org.example.POJOs.AuthTokenResponse;
import org.example.POJOs.SelectResponse;
import org.example.POJOs.WorkerRequest;
import org.example.POJOs.WorkerResponse;
import org.example.service.PostgresqlQueryService;
import org.example.service.WorkerAuthenticationService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WorkerHttpServer {
    private final HttpServer server;
    private final ObjectMapper objectMapper;
    private final PostgresqlQueryService queryService;
    private final WorkerAuthenticationService authService;
    private final String workerId;

    public WorkerHttpServer(int port, String workerId) throws Exception {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.objectMapper = new ObjectMapper();
        this.queryService = new PostgresqlQueryService();
        this.authService = WorkerAuthenticationService.getInstance();
        this.workerId = workerId;
        setupEndpoints();
    }

    private void setupEndpoints() {
        server.createContext("/authenticate", new AuthHandler());
        server.createContext("/execute", new ExecuteHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        System.out.println("Worker " + workerId + " started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        System.out.println("Worker " + workerId + " stopped");
    }

    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                AuthRequest authRequest = objectMapper.readValue(requestBody, AuthRequest.class);

                System.out.println("[Worker " + workerId + "] Authentication attempt for user: " + authRequest.getUsername());

                String token = authService.authenticate(authRequest.getUsername(), authRequest.getPassword());
                AuthTokenResponse response;
                
                if (token != null) {
                    response = new AuthTokenResponse(true, token, "Authentication successful");
                    System.out.println("[Worker " + workerId + "] Authentication successful for user: " + authRequest.getUsername());
                } else {
                    response = new AuthTokenResponse(false, null, "Invalid credentials");
                    System.out.println("[Worker " + workerId + "] Authentication failed for user: " + authRequest.getUsername());
                }

                String jsonResponse = objectMapper.writeValueAsString(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, jsonResponse);

            } catch (Exception e) {
                AuthTokenResponse errorResponse = new AuthTokenResponse(false, null, "Authentication error: " + e.getMessage());
                String jsonResponse = objectMapper.writeValueAsString(errorResponse);
                sendResponse(exchange, 500, jsonResponse);
            }
        }
    }

    private class ExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Check for authentication token in headers
                String authToken = exchange.getRequestHeaders().getFirst("Authorization");
                if (authToken == null || !authToken.startsWith("Bearer ")) {
                    sendResponse(exchange, 401, "{\"error\":\"Missing or invalid authorization header\"}");
                    return;
                }

                String token = authToken.substring(7); // Remove "Bearer " prefix
                if (!authService.validateToken(token)) {
                    sendResponse(exchange, 401, "{\"error\":\"Invalid or expired token\"}");
                    return;
                }

                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                WorkerRequest request = objectMapper.readValue(requestBody, WorkerRequest.class);

                System.out.println("\n[Worker " + workerId + "] Received authenticated request: " + request.getRequestId());
                System.out.println("Query: " + request.getQuery());

                WorkerResponse response = processRequest(request);
                String jsonResponse = objectMapper.writeValueAsString(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, jsonResponse);

            } catch (Exception e) {
                WorkerResponse errorResponse = createErrorResponse("unknown", e.getMessage());
                String jsonResponse = objectMapper.writeValueAsString(errorResponse);
                sendResponse(exchange, 500, jsonResponse);
            }
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"healthy\",\"workerId\":\"" + workerId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            sendResponse(exchange, 200, response);
        }
    }

    private WorkerResponse processRequest(WorkerRequest request) {
        WorkerResponse response = new WorkerResponse();
        response.setRequestId(request.getRequestId());
        response.setWorkerId(workerId);
        response.setTimestamp(System.currentTimeMillis());

        try {
            // Validate request
            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                response.setSuccess(false);
                response.setMessage("Error: Empty or null query provided");
                return response;
            }

            System.out.println("[WORKER-EXEC] Executing query: " + request.getQuery());
            
            SelectResponse selectData = queryService.queryParser(request.getQuery());
            if (selectData != null) {
                response.setData(selectData);
            }
            response.setSuccess(true);
            response.setMessage("Query executed successfully on worker " + workerId);
            
            System.out.println("[WORKER-EXEC] ✅ Query completed successfully");
            
        } catch (IllegalArgumentException e) {
            // Handle syntax and validation errors
            response.setSuccess(false);
            response.setMessage("Syntax Error: " + e.getMessage());
            System.err.println("[WORKER-EXEC] ❌ Syntax error: " + e.getMessage());
            
        } catch (org.example.exception.FileIOException e) {
            // Handle file I/O errors
            response.setSuccess(false);
            response.setMessage("Database Error: " + e.getMessage());
            System.err.println("[WORKER-EXEC] ❌ Database error: " + e.getMessage());
            
        } catch (java.io.IOException e) {
            // Handle I/O errors
            response.setSuccess(false);
            response.setMessage("I/O Error: " + e.getMessage());
            System.err.println("[WORKER-EXEC] ❌ I/O error: " + e.getMessage());
            
        } catch (RuntimeException e) {
            // Handle runtime errors
            response.setSuccess(false);
            response.setMessage("Runtime Error: " + e.getMessage());
            System.err.println("[WORKER-EXEC] ❌ Runtime error: " + e.getMessage());
            e.printStackTrace();
            
        } catch (Exception e) {
            // Handle any other unexpected errors
            response.setSuccess(false);
            response.setMessage("Unexpected Error: " + e.getMessage());
            System.err.println("[WORKER-EXEC] ❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    private WorkerResponse createErrorResponse(String requestId, String message) {
        WorkerResponse response = new WorkerResponse();
        response.setRequestId(requestId);
        response.setWorkerId(workerId);
        response.setSuccess(false);
        response.setMessage(message);
        response.setTimestamp(System.currentTimeMillis());
        return response;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
