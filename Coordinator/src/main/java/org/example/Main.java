package org.example;

import org.example.model.WorkerNode;
import org.example.service.DistributedQueryCoordinator;
import org.example.service.WorkerAuthenticationManager;
import org.example.service.WorkerCredentialsManager;
import org.example.service.WorkerRegistry;
import org.example.ui.ConsoleUserInterface;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Distributed Database Coordinator ===\n");
        
        // Initialize authentication system
        System.out.println("[COORDINATOR-AUTH] 🚀 Initializing authentication system...");
        WorkerCredentialsManager.getInstance(); // This will load credentials and show logs
        WorkerAuthenticationManager.getInstance(); // Initialize auth manager
        System.out.println("[COORDINATOR-AUTH] ✅ Authentication system ready\n");
        
        // Initialize worker registry
        WorkerRegistry registry = WorkerRegistry.getInstance();
        
        // Load worker configuration
        loadWorkerConfig(registry);
        
        // Initialize distributed query coordinator
        DistributedQueryCoordinator coordinator = new DistributedQueryCoordinator();
        
        // Show initial worker status
        coordinator.showWorkerStatus();
        
        // Start the console interface
        ConsoleUserInterface userInterface = new ConsoleUserInterface(coordinator);
        userInterface.startScreen();
        
        // Cleanup on exit
        Runtime.getRuntime().addShutdownHook(new Thread(coordinator::shutdown));
    }
    
    private static void loadWorkerConfig(WorkerRegistry registry) {
        try (BufferedReader br = new BufferedReader(new FileReader("workers.config"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String workerId = parts[0].trim();
                    String host = parts[1].trim();
                    int port = Integer.parseInt(parts[2].trim());
                    
                    WorkerNode worker = new WorkerNode(workerId, host, port, true, System.currentTimeMillis());
                    registry.registerWorker(worker);
                    System.out.println("Registered worker: " + workerId + " at " + host + ":" + port);
                }
            }
        } catch (IOException e) {
            System.out.println("Warning: Could not load workers.config. Using default configuration.");
            System.out.println("Create a workers.config file with format: workerId,host,port");
        }
    }
}
