package org.example.util.TokenUtility;

public interface ITokenUtil {
    /**
     * Generates a unique token string.
     *
     * @param username The username for which the token is generated.
     * @return A unique token string.
     */
    String generateToken(String username);

    /**
     * Validates the given token string.
     *
     * @param token The token string to be validated.
     * @return True if the token is valid, false otherwise.
     */
    boolean validateToken(String token);
}
