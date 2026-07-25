package com.hilo.rewards.repository;

import com.hilo.rewards.model.Course;
import com.hilo.rewards.model.Group;
import com.hilo.rewards.model.GroupMember;
import com.hilo.rewards.model.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class SupabaseRepository {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public SupabaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Group getGroup(Long groupId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id, name, course_id, estado FROM groups WHERE id = ?",
                (rs, rowNum) -> new Group(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getLong("course_id"),
                    rs.getString("estado")
                ),
                groupId
            );
        } catch (Exception e) {
            log.warn("Group not found: {}", groupId);
            return null;
        }
    }

    public Course getCourse(Long courseId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id, title, is_active FROM courses WHERE id = ?",
                (rs, rowNum) -> new Course(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getBoolean("is_active")
                ),
                courseId
            );
        } catch (Exception e) {
            log.warn("Course not found: {}", courseId);
            return null;
        }
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return jdbcTemplate.query(
            "SELECT id, group_id, user_id, approved FROM group_members WHERE group_id = ?",
            (rs, rowNum) -> new GroupMember(
                rs.getLong("id"),
                rs.getLong("group_id"),
                rs.getString("user_id"),
                rs.getBoolean("approved")
            ),
            groupId
        );
    }

    public Profile getProfile(String userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id, full_name, wallet_address, role FROM profiles WHERE id = ?",
                (rs, rowNum) -> new Profile(
                    rs.getString("id"),
                    rs.getString("full_name"),
                    rs.getString("wallet_address"),
                    rs.getString("role")
                ),
                userId
            );
        } catch (Exception e) {
            log.warn("Profile not found: {}", userId);
            return null;
        }
    }

    public void saveReward(String rewardId, Long groupId, String idempotencyKey, String txHash, String status, BigDecimal totalAmount) {
        jdbcTemplate.update(
            """
            INSERT INTO rewards (id, group_id, idempotency_key, transaction_hash, status, total_amount)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO UPDATE SET
              transaction_hash = EXCLUDED.transaction_hash,
              status = EXCLUDED.status,
              updated_at = now()
            """,
            UUID.fromString(rewardId), groupId, idempotencyKey, txHash, status, totalAmount
        );
    }

    public void saveRewardRecipients(String rewardId, List<String> wallets, BigDecimal amountPerRecipient) {
        for (String wallet : wallets) {
            jdbcTemplate.update(
                """
                INSERT INTO reward_recipients (reward_id, user_id, wallet_address, amount)
                SELECT ?, p.id, p.wallet_address, ?
                FROM profiles p WHERE p.wallet_address = ?
                """,
                UUID.fromString(rewardId), amountPerRecipient, wallet
            );
        }
    }

    public void saveRewardAttempt(String rewardId, String action, String status, String errorMessage) {
        jdbcTemplate.update(
            "INSERT INTO reward_attempts (reward_id, action, status, error_message) VALUES (?, ?, ?, ?)",
            UUID.fromString(rewardId), action, status, errorMessage
        );
    }

    public void saveAuditEvent(String userId, String userRole, String action, Long groupId, String details, String ipHash) {
        jdbcTemplate.update(
            "INSERT INTO security_audit_events (user_id, user_role, action, group_id, details, ip_hash) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
            userId, userRole, action, groupId, details, ipHash
        );
    }

    public void updateGroupEstado(Long groupId, String estado) {
        jdbcTemplate.update("UPDATE groups SET estado = ? WHERE id = ?", estado, groupId);
    }

    public void updateRewardStatus(String rewardId, String status) {
        jdbcTemplate.update("UPDATE rewards SET status = ?, updated_at = now() WHERE id = ?", status, UUID.fromString(rewardId));
    }

    public void updateRewardBlockNumber(String rewardId, Long blockNumber) {
        jdbcTemplate.update("UPDATE rewards SET block_number = ?, status = 'confirmed', updated_at = now() WHERE id = ?", blockNumber, UUID.fromString(rewardId));
    }

    public boolean isRewardProcessedOnChain(String rewardId) {
        try {
            Boolean result = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM rewards WHERE id = ? AND transaction_hash IS NOT NULL)",
                Boolean.class,
                UUID.fromString(rewardId)
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
