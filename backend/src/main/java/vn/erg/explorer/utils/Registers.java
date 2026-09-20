package vn.erg.explorer.utils;

import vn.erg.explorer.dtos.RegisterDto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort decoder for box registers (R4–R9) as serialized by the node: a Sigma type code
 * followed by the value. Only the common scalar and Coll[Byte] types are rendered; anything else
 * keeps its raw hex with the type marked unknown.
 */
public final class Registers {

    private Registers() {
    }

    public static RegisterDto decode(String key, String raw) {
        RegisterDto dto = new RegisterDto();
        dto.setKey(key);
        dto.setRaw(raw);
        try {
            byte[] bytes = Hex.decode(raw);
            Reader r = new Reader(bytes);
            int type = r.u8();
            switch (type) {
                case 0x01 -> set(dto, "Boolean", String.valueOf(r.u8() != 0));
                case 0x02 -> set(dto, "Byte", String.valueOf((byte) r.u8()));
                case 0x03 -> set(dto, "Short", String.valueOf(r.zigzag()));
                case 0x04 -> set(dto, "Int", String.valueOf(r.zigzag()));
                case 0x05 -> set(dto, "Long", String.valueOf(r.zigzag()));
                case 0x06 -> set(dto, "BigInt", new BigInteger(r.bytes(r.vlq())).toString());
                case 0x07 -> set(dto, "GroupElement", Hex.encode(r.bytes(33)));
                case 0x08 -> set(dto, "SigmaProp", Hex.encode(r.rest()));
                case 0x0e -> set(dto, "Coll[Byte]", collBytes(r.bytes(r.vlq())));
                case 0x10 -> set(dto, "Coll[Int]", collNumbers(r));
                case 0x11 -> set(dto, "Coll[Long]", collNumbers(r));
                default -> set(dto, "Unknown (0x" + Integer.toHexString(type) + ")", raw);
            }
        } catch (RuntimeException e) {
            set(dto, "Unparsed", raw);
        }
        return dto;
    }

    private static void set(RegisterDto dto, String type, String value) {
        dto.setType(type);
        dto.setValue(value);
    }

    /** Printable UTF-8 renders as a quoted string (token names, order metadata), otherwise hex. */
    private static String collBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return "\"\"";
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        boolean printable = !text.contains("�")
                && text.chars().allMatch(c -> c >= 0x20 && c != 0x7f || c == '\n' || c == '\t');
        return printable ? "\"" + text + "\"" : Hex.encode(bytes);
    }

    private static String collNumbers(Reader r) {
        int n = (int) r.vlq();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n && i < 64; i++) {
            out.add(String.valueOf(r.zigzag()));
        }
        return "[" + String.join(", ", out) + (n > 64 ? ", …" : "") + "]";
    }

    private static final class Reader {
        private final byte[] b;
        private int pos;

        Reader(byte[] b) {
            this.b = b;
        }

        int u8() {
            return b[pos++] & 0xff;
        }

        long vlq() {
            long result = 0;
            int shift = 0;
            while (true) {
                int x = u8();
                result |= (long) (x & 0x7f) << shift;
                if ((x & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
        }

        long zigzag() {
            long n = vlq();
            return (n >>> 1) ^ -(n & 1);
        }

        byte[] bytes(long n) {
            byte[] out = new byte[(int) n];
            System.arraycopy(b, pos, out, 0, (int) n);
            pos += (int) n;
            return out;
        }

        byte[] rest() {
            return bytes(b.length - pos);
        }
    }

}
