package org.example.POJOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.model.User;

@Data
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private User user;
}
