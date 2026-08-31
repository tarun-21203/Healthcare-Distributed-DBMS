package org.example.service;

import org.example.POJOs.AuthResponse;
import org.example.constant.AppConstant;
import org.example.exception.AuthException;
import org.example.exception.FileIOException;
import org.example.exception.TokenException;
import org.example.interfaces.IAuditLogger;
import org.example.interfaces.IUserAuthentication;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.util.HashingUtility.IHashingUtil;
import org.example.util.HashingUtility.MD5HashingUtil;
import org.example.util.TokenUtility.CustomTokenUtil;
import org.example.util.TokenUtility.ITokenUtil;

public class UserAuthService implements IUserAuthentication {

    private Integer captchaResult;
    private final IHashingUtil hashingUtil;
    private final ITokenUtil tokenUtil;
    private final UserRepository userRepository;
    private final IAuditLogger auditLoggerService;

    public UserAuthService() throws FileIOException {
        hashingUtil = new MD5HashingUtil();
        userRepository = new UserRepository();
        tokenUtil = new CustomTokenUtil();
        auditLoggerService = new AuditLoggerService();
    }

    @Override
    public AuthResponse authenticate(String username, String password, int captchaExpected) throws AuthException, FileIOException {
        if (captchaResult != captchaExpected) {
            auditLoggerService.logAudit(username, "Captcha verification failed", AppConstant.AUDIT_LOG_TYPE.FAILED_LOGIN);
            throw new AuthException("Captcha verification failed");
        }
        User user = userRepository.getByUserId(username);
        if (user == null || !user.isActive()) {
            auditLoggerService.logAudit(username, "User not found", AppConstant.AUDIT_LOG_TYPE.FAILED_LOGIN);
            throw new AuthException("User not found");
        }
        String hashedInputPassword = hashingUtil.hashText(password);
        if (!user.getHashedPassword().equals(hashedInputPassword)) {
            auditLoggerService.logAudit(username, "Invalid password", AppConstant.AUDIT_LOG_TYPE.FAILED_LOGIN);
            throw new AuthException("Invalid password");
        }
        auditLoggerService.logAudit(username, "Authentication successful", AppConstant.AUDIT_LOG_TYPE.SUCCESS_LOGIN);
        String lastLoginTime = auditLoggerService.lastSuccessfulLogin(username);
        return new AuthResponse(true, "Authentication successful\nLast successful login: " + lastLoginTime, user);
    }

    @Override
    public AuthResponse signup(String username, String password, String securityQuestion, String securityAnswer) throws AuthException, FileIOException {
        User existingUser = userRepository.getByUserId(username);
        if (existingUser != null) {
            throw new AuthException("User already exists");
        }

        User user = User.builder()
                .userId(username)
                .hashedPassword(hashingUtil.hashText(password))
                .securityQuestion(securityQuestion)
                .hashedSecurityAnswer(hashingUtil.hashText(securityAnswer))
                .isActive(true)
                .isAdmin(false)
                .build();
        boolean status = userRepository.saveUser(user);
        if (!status) {
            auditLoggerService.logAudit(username, "System Error", AppConstant.AUDIT_LOG_TYPE.FAILED_SIGNUP);
            throw new AuthException("Signup failed");
        }
        auditLoggerService.logAudit(username, "Signup successful", AppConstant.AUDIT_LOG_TYPE.SUCCESS_SIGNUP);
        return new AuthResponse(true, "Signup successful", user);
    }

    @Override
    public AuthResponse recoverPassword(String username, String newPassword, String newConfirmPassword, String token) throws AuthException, TokenException, FileIOException {
        if (!tokenUtil.validateToken(token)) {
            auditLoggerService.logAudit(username, "Invalid or expired token", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new TokenException("Invalid or expired token");
        }
        if (!newPassword.equals(newConfirmPassword)) {
            auditLoggerService.logAudit(username, "Passwords do not match", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("Passwords do not match");
        }
        boolean user = userRepository.updatePassword(username, hashingUtil.hashText(newPassword));
        if (!user) {
            auditLoggerService.logAudit(username, "System Error", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("Password recovery failed");
        }
        auditLoggerService.logAudit(username, "Password recovery successful", AppConstant.AUDIT_LOG_TYPE.SUCCESS_RECOVER_PASSWORD);
        return new AuthResponse(true, "Password recovery successful", null);
    }

    @Override
    public String validateSecurityAnswer(String username, String securityAnswer) throws AuthException, FileIOException {
        User user = userRepository.getByUserId(username);
        if (user == null) {
            auditLoggerService.logAudit(username, "User not found", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("User not found");
        }
        if (!user.getHashedSecurityAnswer().equals(hashingUtil.hashText(securityAnswer))) {
            auditLoggerService.logAudit(username, "Security answer is incorrect", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("Security answer is incorrect");
        }
        return tokenUtil.generateToken(username);
    }

    public String generateCaptcha() {
        int a = (int) (Math.random() * 20);
        int b = (int) (Math.random() * 20);
        int operation = (int) (Math.random() * 3);
        switch (operation) {
            case 0:
                captchaResult = a + b;
                return "%d + %d =".formatted(a, b);
            case 1:
                if (a < b) {
                    int temp = a;
                    a = b;
                    b = temp;
                }
                captchaResult = a - b;
                return "%d - %d =".formatted(a, b);
            case 2:
                captchaResult = a * b;
                return "%d * %d =".formatted(a, b);
        }
        return null;
    }

    public String getSecurityQuestion(String username) throws FileIOException, AuthException {
        User user = userRepository.getByUserId(username);
        if (user == null) {
            auditLoggerService.logAudit(username, "User not found", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("User not found");
        } else if (user.isAdmin()) {
            auditLoggerService.logAudit(username, "No Recovery for Admin", AppConstant.AUDIT_LOG_TYPE.FAILED_RECOVER_PASSWORD);
            throw new AuthException("No Recovery for Admin");

        }
        return user.getSecurityQuestion();

    }
}
