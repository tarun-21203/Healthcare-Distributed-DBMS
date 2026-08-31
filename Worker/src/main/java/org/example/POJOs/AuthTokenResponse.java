package org.example.POJOs;

public class AuthTokenResponse {
    private boolean success;
    private String token;
    private String message;
    private long timestamp;

    public AuthTokenResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public AuthTokenResponse(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AuthTokenResponse{" +
                "success=" + success +
                ", token='" + (token != null ? "[TOKEN]" : "null") + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}