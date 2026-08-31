package org.example.util.HashingUtility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5HashingUtil implements IHashingUtil {

    @Override
    public String hashText(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verifyHash(String input, String hash) {
        if (hashText(input).equals(hash)) {
            return true;
        }
        return false;
    }
}
