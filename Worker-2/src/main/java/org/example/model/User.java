package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    String userId;
    String hashedPassword;
    String securityQuestion;
    String hashedSecurityAnswer;
    boolean isActive;
    @Builder.Default
    boolean isAdmin = false;
}
