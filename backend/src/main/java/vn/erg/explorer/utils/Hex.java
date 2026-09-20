package vn.erg.explorer.utils;

import java.util.HexFormat;

public final class Hex {

    private static final HexFormat FORMAT = HexFormat.of();

    private Hex() {
    }

    public static byte[] decode(String hex) {
        return FORMAT.parseHex(hex);
    }

    public static String encode(byte[] bytes) {
        return FORMAT.formatHex(bytes);
    }

    public static boolean isHex64(String s) {
        return s != null && s.length() == 64 && s.chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

}
