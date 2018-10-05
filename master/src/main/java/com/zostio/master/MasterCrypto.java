package com.zostio.master;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class MasterCrypto {

    public String generateHash(String password, String stringSalt) {
        byte[] salt = stringSalt.getBytes();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(salt);
            byte[] hash = digest.digest(password.getBytes());
            return bytesToStringHex(hash);
        }catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    private final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private String bytesToStringHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length*2];
        for(int j=0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v >>> 4];
            hexChars[j*2+1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public String createSalt() {
        byte[] bytes = new byte[20];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);
        String stringSalt = bytesToStringHex(bytes);
        return stringSalt;
    }
}
