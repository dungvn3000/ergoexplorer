package vn.erg.explorer.utils;

import org.bouncycastle.crypto.digests.Blake2bDigest;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Mainnet address encoding, done locally so a block with hundreds of boxes needs no
 * /utils/ergoTreeToAddress round-trips.
 * <p>
 * address = base58(prefix ++ content ++ blake2b256(prefix ++ content)[0..4]) where
 * prefix = network (0x00 mainnet) | type (1 = P2PK, 3 = P2S) and content is the public key
 * for P2PK trees ({@code 0008cd<33-byte pk>}) or the whole tree for P2S.
 */
public final class ErgoAddress {

    private static final byte MAINNET = 0x00;
    private static final byte P2PK = 0x01;
    private static final byte P2S = 0x03;
    private static final String P2PK_TREE_PREFIX = "0008cd";

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger FIFTY_EIGHT = BigInteger.valueOf(58);

    private ErgoAddress() {
    }

    public static String fromErgoTree(String ergoTree) {
        byte[] tree = Hex.decode(ergoTree);
        if (ergoTree.startsWith(P2PK_TREE_PREFIX) && tree.length == 36) {
            return encode((byte) (MAINNET | P2PK), Arrays.copyOfRange(tree, 3, 36));
        }
        return encode((byte) (MAINNET | P2S), tree);
    }

    /** P2PK address of a miner from the public key in the block header ({@code powSolutions.pk}). */
    public static String fromPublicKey(String pkHex) {
        return encode((byte) (MAINNET | P2PK), Hex.decode(pkHex));
    }

    public static boolean isP2PK(String address) {
        return address != null && address.startsWith("9");
    }

    /**
     * Syntactic check done locally: base58, mainnet prefix (P2PK / P2SH / P2S) and a valid Blake2b
     * checksum. No minimum length — a P2S address of a tiny script ({@code sigmaProp(true)} is
     * {@code W76SLPLFea}, 10 characters) is as valid as a 51-character P2PK one.
     */
    public static boolean looksLikeAddress(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        byte[] full = base58Decode(s);
        if (full == null || full.length < 1 + 1 + 4) {
            return false;
        }
        int type = full[0] & 0x0f;
        if ((full[0] & 0xf0) != MAINNET || (type != P2PK && type != 0x02 && type != P2S)) {
            return false;
        }
        int bodyLen = full.length - 4;
        byte[] hash = blake2b(full, bodyLen);
        for (int i = 0; i < 4; i++) {
            if (hash[i] != full[bodyLen + i]) {
                return false;
            }
        }
        return true;
    }

    private static String encode(byte prefix, byte[] content) {
        byte[] withPrefix = new byte[content.length + 1];
        withPrefix[0] = prefix;
        System.arraycopy(content, 0, withPrefix, 1, content.length);

        byte[] hash = blake2b(withPrefix, withPrefix.length);

        byte[] full = Arrays.copyOf(withPrefix, withPrefix.length + 4);
        System.arraycopy(hash, 0, full, withPrefix.length, 4);
        return base58(full);
    }

    private static byte[] blake2b(byte[] input, int length) {
        Blake2bDigest digest = new Blake2bDigest(256);
        digest.update(input, 0, length);
        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        return hash;
    }

    /** Null when a character is outside the alphabet. */
    private static byte[] base58Decode(String s) {
        BigInteger n = BigInteger.ZERO;
        int leadingZeros = 0;
        boolean leading = true;
        for (int i = 0; i < s.length(); i++) {
            int d = ALPHABET.indexOf(s.charAt(i));
            if (d < 0) {
                return null;
            }
            if (leading && d == 0) {
                leadingZeros++;
            } else {
                leading = false;
            }
            n = n.multiply(FIFTY_EIGHT).add(BigInteger.valueOf(d));
        }
        byte[] digits = n.toByteArray();
        int start = digits.length > 1 && digits[0] == 0 ? 1 : 0;   // drop BigInteger's sign byte
        byte[] out = new byte[leadingZeros + digits.length - start];
        System.arraycopy(digits, start, out, leadingZeros, digits.length - start);
        return out;
    }

    private static String base58(byte[] input) {
        BigInteger n = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(FIFTY_EIGHT);
            sb.append(ALPHABET.charAt(qr[1].intValue()));
            n = qr[0];
        }
        for (byte b : input) {
            if (b != 0) {
                break;
            }
            sb.append(ALPHABET.charAt(0));
        }
        return sb.reverse().toString();
    }

}
