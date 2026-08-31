package org.example.util.HashingUtility;

public interface IHashingUtil {
    /**
     * Generates a hash for the given text.
     *
     * @param input The input text to be hashed.
     * @return The generated hash as a String.
     */
    String hashText(String input);
    /**
     * Verifies if the given text matches the provided hash.
     *
     * @param input The input text to be verified.
     * @param hash The hash to compare against.
     * @return True if the text matches the hash, false otherwise.
     */
    boolean verifyHash(String input, String hash);
}
