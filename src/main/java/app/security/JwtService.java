package app.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal HS256 JSON Web Token implementation (no external library needed). */
public class JwtService {

    private static final Pattern SUB = Pattern.compile("\"sub\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern IAT = Pattern.compile("\"iat\":(\\d+)");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(String secret, long expirationMs) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters long");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationMs / 1000;
    }

    public String generateToken(String email, String role) {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + escape(email) + "\",\"role\":\"" + escape(role)
                + "\",\"iat\":" + now + ",\"exp\":" + (now + expirationSeconds) + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    /** Returns the email (subject) if the token is genuine and not expired. */
    public Optional<String> extractEmail(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            String payload = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8);
            Matcher exp = EXP.matcher(payload);
            Matcher sub = SUB.matcher(payload);
            if (!exp.find() || !sub.find()) return Optional.empty();
            if (Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(unescape(sub.group(1)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Issue time (epoch seconds) of a token that extractEmail has already accepted. */
    public Optional<Long> extractIssuedAt(String token) {
        try {
            String payload = new String(DEC.decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            Matcher m = IAT.matcher(payload);
            return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign token", e);
        }
    }

    private static String encode(String s) {
        return ENC.encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
