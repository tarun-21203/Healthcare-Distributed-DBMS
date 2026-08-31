package org.example.ui;

import org.example.service.DistributedQueryCoordinator;
import org.example.util.InputReaderUtility.ConsoleInputReaderUtil;
import org.example.util.InputReaderUtility.IInputReaderUtil;

public class ConsoleUserInterface {

    private final IInputReaderUtil inputReader;
    private final DistributedQueryCoordinator distributedCoordinator;

    public ConsoleUserInterface(DistributedQueryCoordinator coordinator) {
        this.inputReader = new ConsoleInputReaderUtil();
        this.distributedCoordinator = coordinator;
    }

    public void startScreen() {
        displayMessage("\n=== Distributed Database Coordinator ===");
        displayMessage("Type 'help' for available commands\n");
        handleQuerying();
    }

    private void displayMessage(String message) {
        System.out.println(message);
    }

    private void displayError(String errorMessage) {
        System.out.println("[ERROR]: " + errorMessage);
    }

    private void handleQuerying() {
        try {
            while (true) {
                String sqlQuery = inputReader.readLine("SQL> ");
                String lowerQuery = sqlQuery.toLowerCase().trim();
                
                if (lowerQuery.equals("exit") || lowerQuery.equals("quit")) {
                    displayMessage("Goodbye!");
                    return;
                } else if (lowerQuery.equals("status")) {
                    distributedCoordinator.showWorkerStatus();
                } else if (lowerQuery.equals("help")) {
                    showHelp();
                } else if (!sqlQuery.trim().isEmpty()) {
                    distributedCoordinator.executeDistributedQuery(sqlQuery);
                }
            }
        } catch (Exception e) {
            displayError(e.getMessage());
            handleQuerying();
        }
    }
    
    private void showHelp() {
        displayMessage("\n=== Available Commands ===");
        displayMessage("\nSystem Commands:");
        displayMessage("  status              - Show worker status");
        displayMessage("  show metadata       - Show distribution metadata");
        displayMessage("  help                - Show this help");
        displayMessage("  exit / quit         - Exit coordinator");
        
        displayMessage("\nDatabase Commands:");
        displayMessage("  SHOW DATABASES");
        displayMessage("  CREATE DATABASE <name>");
        displayMessage("  USE <database>");
        displayMessage("  DROP DATABASE <name>");
        
        displayMessage("\nTable Commands:");
        displayMessage("  CREATE TABLE <name> (<columns>) ON <worker1>, <worker2>;");
        displayMessage("  CREATE TABLE <name> (<columns>) ON <workers>");
        displayMessage("    HORIZONTAL <column> RANGE <worker1>: <cond1>, <worker2>: <cond2>;");
        displayMessage("  CREATE TABLE <name> (<columns>) ON <workers>");
        displayMessage("    VERTICAL <worker1>: <col1>,<col2>, <worker2>: <col3>,<col4>;");
        displayMessage("  DROP TABLE <table>");
        displayMessage("  DESCRIBE <table>");
        
        displayMessage("\nData Commands:");
        displayMessage("  INSERT INTO <table> VALUES (<values>)");
        displayMessage("  SELECT * FROM <table>");
        displayMessage("  SELECT <columns> FROM <table> WHERE <condition>");
        displayMessage("  UPDATE <table> SET <col>=<val> WHERE <condition>");
        displayMessage("  DELETE FROM <table> WHERE <condition>");
        
        displayMessage("\nDistribution Commands:");
        displayMessage("  REPLICATE TABLE <table> ON <worker1>, <worker2>;");
        displayMessage("  FRAGMENT TABLE <table> HORIZONTAL ON <column>");
        displayMessage("    RANGE <worker1>: <condition1>, <worker2>: <condition2>;");
        displayMessage("  FRAGMENT TABLE <table> VERTICAL");
        displayMessage("    COLUMNS <worker1>: <col1>,<col2>, <worker2>: <col3>,<col4>;");
        
        displayMessage("\nExamples:");
        displayMessage("  CREATE DATABASE shop;");
        displayMessage("  USE shop;");
        displayMessage("  CREATE TABLE products (id INT, name VARCHAR, price INT) ON worker-1, worker-2;");
        displayMessage("  INSERT INTO products VALUES (1, 'Laptop', 1000);");
        displayMessage("  SELECT * FROM products;");
        displayMessage("");
    }
}
