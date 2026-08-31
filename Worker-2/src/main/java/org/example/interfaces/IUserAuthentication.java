package org.example.interfaces;

import org.example.POJOs.AuthResponse;
import org.example.exception.AuthException;
import org.example.exception.FileIOException;
import org.example.exception.TokenException;

public interface IUserAuthentication {
    /**
     * Authenticates a user with the given username, password, and captcha value.
     *
     * @param username        the username of the user
     * @param password        the password of the user
     * @param captchaExpected the expected captcha value
     * @return AuthResponse containing authentication result
     * @throws AuthException if authentication fails
     */
    AuthResponse authenticate(String username, String password, int captchaExpected) throws AuthException, FileIOException;

    /**
     * Signs up a new user with the given details.
     *
     * @param username         the username of the new user
     * @param password         the password of the new user
     * @param securityQuestion the security question for password recovery
     * @param securityAnswer   the answer to the security question
     * @return AuthResponse containing signup result
     * @throws AuthException if signup fails
     */
    AuthResponse signup(String username, String password, String securityQuestion, String securityAnswer) throws AuthException, FileIOException;

    /**
     * Recovers the password for a user with the given username and security answer.
     *
     * @param username    the username of the user
     * @param newPassword the new password to set
     * @param newConfirmPassword the confirmation of the new password
     * @param token the token received after validating security answer
     * @return AuthResponse containing password recovery result
     * @throws AuthException if password recovery fails
     */
    AuthResponse recoverPassword(String username, String newPassword, String newConfirmPassword, String token) throws AuthException, TokenException, FileIOException;

    /**
     * Validates the security answer for the given username.
     *
     * @param username       the username of the user
     * @param securityAnswer the answer to the security question
     * @return the token to be used for password recovery
     * @throws AuthException if the security answer is incorrect
     */
    String validateSecurityAnswer(String username, String securityAnswer) throws AuthException, FileIOException;

    /**
     * Generates a captcha challenge for the user.
     *
     * @return the captcha challenge as a Strings
     */
    String generateCaptcha();

    /**
     * Retrieves the security question for the given username.
     *
     * @param username the username of the user
     * @return the security question as a String
     */
    String getSecurityQuestion(String username) throws FileIOException, AuthException;

}
