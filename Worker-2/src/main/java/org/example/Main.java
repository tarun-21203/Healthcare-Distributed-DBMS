package org.example;

import org.example.server.WorkerHttpServer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Default configuration
        int port = 8082;
        String workerId = "worker-2";
        
        // Load configuration from file or command line args
        if (args.length >= 2) {
            workerId = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            loadWorkerConfig(new String[]{workerId, String.valueOf(port)});
        }
        
        try {
            System.out.println("=== Distributed Database Worker ===");
            System.out.println("Worker ID: " + workerId);
            System.out.println("Port: " + port);
            
            WorkerHttpServer server = new WorkerHttpServer(port, workerId);
            server.start();
            
            System.out.println("\nWorker is running. Press Enter to stop...");
            System.in.read();
            
            server.stop();
        } catch (Exception e) {
            System.err.println("Failed to start worker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void loadWorkerConfig(String[] config) {
        try (BufferedReader br = new BufferedReader(new FileReader("worker.config"))) {
            String line = br.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    config[0] = parts[0].trim();
                    config[1] = parts[1].trim();
                }
            }
        } catch (IOException e) {
            System.out.println("Using default configuration (worker.config not found)");
        }
    }
}