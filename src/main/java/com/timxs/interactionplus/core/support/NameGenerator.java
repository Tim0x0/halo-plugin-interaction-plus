package com.timxs.interactionplus.core.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * metadata.name 生成工具。
 *
 * <p>生成结果符合 Halo metadata.name 规则：小写字母、数字、连字符。
 */
public final class NameGenerator {

    private NameGenerator() {
    }

    /** Crockford Base32 字母表（小写，去除 i l o u）。 */
    private static final char[] BASE32 = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");

    /**
     * 生成形如 {@code <prefix>-<ulid>} 的内部名。
     *
     * @param prefix 资源前缀，例如 asset、grant、category
     */
    public static String generate(String prefix) {
        return prefix + "-" + ulid();
    }

    /**
     * 生成 26 字符小写 ULID（48bit 时间 + 80bit 随机）。
     */
    public static String ulid() {
        long timestamp = System.currentTimeMillis();
        byte[] bytes = new byte[16];
        // 高 48 位为时间戳
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        // 低 80 位随机
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        System.arraycopy(randomness, 0, bytes, 6, 10);
        return encodeBase32(bytes);
    }

    /**
     * 为用户生成 Profile 内部名。用户名安全时直接拼接，否则用稳定 hash。
     */
    public static String profileName(String userName) {
        String lower = userName == null ? "" : userName.toLowerCase();
        if (SAFE_NAME.matcher(lower).matches() && lower.length() <= 200) {
            return "profile-" + lower;
        }
        return "profile-" + stableHash(userName);
    }

    /**
     * 取 userName 的 SHA-256 前 32 个十六进制字符作为稳定 hash。
     */
    public static String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String encodeBase32(byte[] data) {
        // 128 bit -> 26 个 base32 字符（每 5 bit 一个字符，最后补足）
        StringBuilder sb = new StringBuilder(26);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32[(buffer >>> bitsLeft) & 0x1f]);
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return sb.toString();
    }
}
