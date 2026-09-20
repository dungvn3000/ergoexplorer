package vn.erg.explorer.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers for the dedupe / lookup keys of the script table. */
public final class Sha256 {

    private Sha256() {
    }

    public static byte[] of(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

}
