package com.hilo.rewards.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final JdbcTemplate jdbcTemplate;
    private final int maxRequests;
    private final int windowSeconds;
    private final int maxGroupRequests;
    private final int groupWindowSeconds;

    public RateLimiter(
            JdbcTemplate jdbcTemplate,
            @Value("${rate-limit.max-requests}") int maxRequests,
            @Value("${rate-limit.window-seconds}") int windowSeconds,
            @Value("${rate-limit.max-group-requests}") int maxGroupRequests,
            @Value("${rate-limit.group-window-seconds}") int groupWindowSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.maxGroupRequests = maxGroupRequests;
        this.groupWindowSeconds = groupWindowSeconds;
    }

    public RateLimitResult checkRateLimit(String userId, String ip, Long groupId) {
        String ipHash = hashIp(ip);

        try {
            Boolean allowed = jdbcTemplate.queryForObject(
                "SELECT atomic_check_rate_limit(?, ?, ?, ?, ?, ?)",
                Boolean.class,
                userId, ipHash, "/api/v1/rewards/process", groupId,
                maxRequests, windowSeconds
            );

            if (Boolean.FALSE.equals(allowed)) {
                log.warn("Rate limited for user={}, ip_hash={}, group={}", userId, ipHash, groupId);
                return new RateLimitResult(false, windowSeconds);
            }

            // Also check group-specific rate limit
            Boolean groupAllowed = jdbcTemplate.queryForObject(
                "SELECT atomic_check_rate_limit(?, ?, ?, ?, ?, ?)",
                Boolean.class,
                null, null, "/api/v1/rewards/process", groupId,
                maxGroupRequests, groupWindowSeconds
            );

            if (Boolean.FALSE.equals(groupAllowed)) {
                log.warn("Group rate limited for group={}", groupId);
                return new RateLimitResult(false, groupWindowSeconds);
            }

            return new RateLimitResult(true, 0);

        } catch (Exception e) {
            log.error("Rate limit check failed", e);
            // Fail open - allow request if rate limiting DB is down
            return new RateLimitResult(true, 0);
        }
    }

    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return ip;
        }
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {}
}
