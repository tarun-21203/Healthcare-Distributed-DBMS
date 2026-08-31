package org.example.POJOs;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class Token {
    private String userId;
    private String value;
    private Instant expiry;

    public boolean isExpired() {
        return Instant.now().isAfter(expiry);
    }
}
