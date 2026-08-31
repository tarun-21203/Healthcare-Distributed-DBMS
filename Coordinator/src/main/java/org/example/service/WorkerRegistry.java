package org.example.service;

import org.example.model.WorkerNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class WorkerRegistry {
    private static WorkerRegistry instance;
    private final List<WorkerNode> workers;

    private WorkerRegistry() {
        this.workers = new CopyOnWriteArrayList<>();
    }

    public static synchronized WorkerRegistry getInstance() {
        if (instance == null) {
            instance = new WorkerRegistry();
        }
        return instance;
    }

    public void registerWorker(WorkerNode worker) {
        workers.removeIf(w -> w.getWorkerId().equals(worker.getWorkerId()));
        workers.add(worker);
        System.out.println("Worker registered: " + worker.getWorkerId() + " at " + worker.getBaseUrl());
    }

    public void unregisterWorker(String workerId) {
        workers.removeIf(w -> w.getWorkerId().equals(workerId));
        System.out.println("Worker unregistered: " + workerId);
    }

    public List<WorkerNode> getActiveWorkers() {
        return workers.stream()
                .filter(WorkerNode::isActive)
                .collect(Collectors.toList());
    }

    public List<WorkerNode> getAllWorkers() {
        return new ArrayList<>(workers);
    }

    public WorkerNode getWorker(String workerId) {
        return workers.stream()
                .filter(w -> w.getWorkerId().equals(workerId))
                .findFirst()
                .orElse(null);
    }

    public void updateHeartbeat(String workerId) {
        WorkerNode worker = getWorker(workerId);
        if (worker != null) {
            worker.setLastHeartbeat(System.currentTimeMillis());
            worker.setActive(true);
        }
    }

    public void markInactive(String workerId) {
        WorkerNode worker = getWorker(workerId);
        if (worker != null) {
            worker.setActive(false);
        }
    }
}
