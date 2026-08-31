package org.example.service;

import org.example.exception.FileIOException;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.util.HashingUtility.IHashingUtil;
import org.example.util.HashingUtility.MD5HashingUtil;
import org.example.util.TokenUtility.CustomTokenUtil;
import org.example.util.TokenUtility.ITokenUtil;

public class WorkerAuthenticationService {
    private static WorkerAuthenticationService instance;
    private final UserRepository userRepository;
    private final IHashingUtil hashingUtil;
    private final ITokenUtil tokenUtil;

    private WorkerAuthenticationService() throws FileIOException {
        this.userRepository = new UserRepository();
        this.hashingUtil = new MD5HashingUtil();
        this.tokenUtil = new CustomTokenUtil();
    }

    public static synchronized WorkerAuthenticationService getInstance() throws FileIOException {
        if (instance == null) {
            instance = new WorkerAuthenticationService();
        }
        return instance;
    }

    public String authenticate(String username, String password) {
        try {
            System.out.println("[WORKER-AUTH] Authenticating user: " + username);
            
            // Get user from repository
            User user = userRepository.getByUserId(username);
            if (user == null) {
                System.err.println("[WORKER-AUTH] User not found: " + username);
                return null;
            }
            
            if (!user.isActive()) {
                System.err.println("[WORKER-AUTH] User is inactive: " + username);
                return null;
            }
            
            // Hash the provided password and compare
            String hashedInputPassword = hashingUtil.hashText(password);
            System.out.println("[WORKER-AUTH] Comparing passwords for user: " + username);
            System.out.println("[WORKER-AUTH] Expected hash: " + user.getHashedPassword());
            System.out.println("[WORKER-AUTH] Provided hash: " + hashedInputPassword);
            
            if (!user.getHashedPassword().equals(hashedInputPassword)) {
                System.err.println("[WORKER-AUTH] Password mismatch for user: " + username);
                return null;
            }
            
            // Authentication successful - generate token
            String token = tokenUtil.generateToken(username);
            System.out.println("[WORKER-AUTH] Authentication successful for user: " + username);
            return token;
            
        } catch (Exception e) {
            System.err.println("[WORKER-AUTH] Authentication error for user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return null; // Authentication failed
    }

    public boolean validateToken(String token) {
        try {
            return tokenUtil.validateToken(token);
        } catch (Exception e) {
            return false;
        }
    }
}