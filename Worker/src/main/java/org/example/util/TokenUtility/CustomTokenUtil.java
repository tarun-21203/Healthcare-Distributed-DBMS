package org.example.util.TokenUtility;

import org.example.POJOs.Token;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CustomTokenUtil implements ITokenUtil{
    private final Map<String, Token> issuedTokens = new HashMap<>();
    private final long tokenExpiry = 10 * 60 * 1000; // 10 minutes in milliseconds

    @Override
    public String generateToken(String username) {
        String uuid = java.util.UUID.randomUUID().toString();
        Instant expiryTime = Instant.now().plusMillis(tokenExpiry);
        Token token = new Token(username, uuid, expiryTime);
        issuedTokens.put(uuid, token);
        System.out.println("[TOKEN] Generated token for user: " + username + " (expires in " + (tokenExpiry / 60000) + " minutes)");
        return uuid;
    }

    @Override
    public boolean validateToken(String token) {
        Token issuedToken = issuedTokens.get(token);
        if (issuedToken == null) {
            System.out.println("[TOKEN] Token not found: " + token.substring(0, Math.min(8, token.length())) + "...");
            return false;
        }
        if (issuedToken.isExpired()) {
            System.out.println("[TOKEN] Token expired for user: " + issuedToken.getUserId());
            issuedTokens.remove(token);
            return false;
        }
        
        // Check if token is close to expiring (within 1 minute)
        long timeUntilExpiry = issuedToken.getExpiry().toEpochMilli() - Instant.now().toEpochMilli();
        if (timeUntilExpiry < 60000) { // Less than 1 minute
            System.out.println("[TOKEN] Token for user " + issuedToken.getUserId() + " expires in " + (timeUntilExpiry / 1000) + " seconds");
        }
        
        return true;
    }
}
