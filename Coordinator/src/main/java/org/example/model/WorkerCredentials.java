package org.example.model;

public class WorkerCredentials {
    private String workerId;
    private String username;
    private String password;

    public WorkerCredentials() {}

    public WorkerCredentials(String workerId, String username, String password) {
        this.workerId = workerId;
        this.username = username;
        this.password = password;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "WorkerCredentials{" +
                "workerId='" + workerId + '\'' +
                ", username='" + username + '\'' +
                ", password='[HIDDEN]'" +
                '}';
    }
}