package com.hilo.rewards.service;

import com.hilo.rewards.exception.BusinessException;
import com.hilo.rewards.exception.ErrorCode;
import com.hilo.rewards.model.*;
import com.hilo.rewards.repository.SupabaseRepository;
import com.hilo.rewards.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    private final SupabaseRepository repository;
    private final BlockchainService blockchainService;
    private final RateLimiter rateLimiter;
    private final int rewardAmount;
    private final int decimals;
    private final String signerAddress;

    public RewardService(
            SupabaseRepository repository,
            BlockchainService blockchainService,
            RateLimiter rateLimiter,
            @Value("${rewards.amount-per-recipient}") int rewardAmount,
            @Value("${rewards.decimals}") int decimals,
            @Value("${blockchain.signer-private-key}") String signerKey) {
        this.repository = repository;
        this.blockchainService = blockchainService;
        this.rateLimiter = rateLimiter;
        this.rewardAmount = rewardAmount;
        this.decimals = decimals;
        this.signerAddress = "derived-from-key";
    }

    @Transactional
    public RewardResponse processReward(RewardRequest request, String userId, String ip) {
        String requestId = UUID.randomUUID().toString();
        String rewardId = generateRewardId(request.courseId(), request.groupId(), request.idempotencyKey());

        log.info("Processing reward: rewardId={}, groupId={}, userId={}", rewardId, request.groupId(), userId);

        // ─── 1. Rate Limiting ──────────────────────────
        RateLimiter.RateLimitResult rateLimit = rateLimiter.checkRateLimit(userId, ip, request.groupId());
        if (!rateLimit.allowed()) {
            repository.saveAuditEvent(userId, "admin", "rate_limited", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.RATE_LIMITED, requestId);
        }

        // ─── 2. Check idempotency ──────────────────────
        if (repository.isRewardProcessedOnChain(rewardId)) {
            log.info("Reward already processed on-chain: {}", rewardId);
            repository.saveAuditEvent(userId, "admin", "idempotent_hit", request.groupId(), rewardId, hashIp(ip));
            throw new BusinessException(ErrorCode.REWARD_ALREADY_EXISTS, requestId);
        }

        // ─── 3. Validate group ─────────────────────────
        Group group = repository.getGroup(request.groupId());
        if (group == null) {
            repository.saveAuditEvent(userId, "admin", "group_not_found", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND, requestId);
        }

        // ─── 4. Validate course ────────────────────────
        Course course = repository.getCourse(group.courseId());
        if (course == null || !course.isActive()) {
            repository.saveAuditEvent(userId, "admin", "course_inactive", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.COURSE_INACTIVE, requestId);
        }

        // ─── 5. Get group members ──────────────────────
        List<GroupMember> members = repository.getGroupMembers(request.groupId());
        if (members.isEmpty()) {
            repository.saveAuditEvent(userId, "admin", "group_empty", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.GROUP_EMPTY, requestId);
        }

        // ─── 6. Check all approved ─────────────────────
        if (members.stream().anyMatch(m -> !m.approved())) {
            repository.saveAuditEvent(userId, "admin", "group_not_completed", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.GROUP_NOT_COMPLETED, requestId);
        }

        // ─── 7. Get and validate wallets ───────────────
        List<String> wallets = new ArrayList<>();
        Set<String> seenWallets = new HashSet<>();

        for (GroupMember member : members) {
            Profile profile = repository.getProfile(member.userId());
            if (profile == null || profile.walletAddress() == null || profile.walletAddress().isBlank()) {
                repository.saveAuditEvent(userId, "admin", "member_without_wallet", request.groupId(), member.userId(), hashIp(ip));
                throw new BusinessException(ErrorCode.MEMBER_WITHOUT_WALLET, requestId);
            }

            String wallet = profile.walletAddress();
            validateWallet(wallet, requestId);

            String normalized = wallet.toLowerCase();
            if (!seenWallets.add(normalized)) {
                repository.saveAuditEvent(userId, "admin", "duplicate_wallet", request.groupId(), wallet, hashIp(ip));
                throw new BusinessException(ErrorCode.DUPLICATE_WALLET, requestId);
            }
            wallets.add(wallet);
        }

        // ─── 8. Calculate amount ───────────────────────
        BigDecimal amountPerRecipient = BigDecimal.valueOf(rewardAmount)
            .multiply(BigDecimal.TEN.pow(decimals));
        BigDecimal totalAmount = amountPerRecipient.multiply(BigDecimal.valueOf(wallets.size()));

        // ─── 9. Check ETH balance ──────────────────────
        BigInteger ethBalance = blockchainService.getBalance();
        BigInteger minGas = BigInteger.valueOf(21000).multiply(BigInteger.valueOf(100_000_000_000L));
        if (ethBalance.compareTo(minGas) < 0) {
            repository.saveAuditEvent(userId, "admin", "insufficient_gas", request.groupId(), null, hashIp(ip));
            throw new BusinessException(ErrorCode.INSUFFICIENT_GAS, requestId);
        }

        // ─── 10. Save reward (processing) ──────────────
        repository.saveReward(rewardId, request.groupId(), request.idempotencyKey(), null, "processing", totalAmount);
        repository.saveRewardRecipients(rewardId, wallets, amountPerRecipient);
        repository.updateGroupEstado(request.groupId(), "processing");
        repository.saveAuditEvent(userId, "admin", "reward_created", request.groupId(), rewardId, hashIp(ip));

        // ─── 11. Send blockchain transaction ───────────
        try {
            repository.saveRewardAttempt(rewardId, "transaction_sent", "submitted", null);
            String txHash = blockchainService.processReward(rewardId, wallets, amountPerRecipient.toBigInteger());
            repository.saveReward(rewardId, request.groupId(), request.idempotencyKey(), txHash, "submitted", totalAmount);

            repository.saveAuditEvent(userId, "admin", "transaction_sent", request.groupId(), txHash, hashIp(ip));

            // ─── 12. Build response ────────────────────
            List<RewardResponse.RecipientAmount> recipients = wallets.stream()
                .map(w -> new RewardResponse.RecipientAmount(w, amountPerRecipient.toBigInteger().toString()))
                .toList();

            return new RewardResponse(rewardId, "submitted", txHash, recipients);

        } catch (Exception e) {
            log.error("Blockchain transaction failed for reward {}", rewardId, e);
            repository.saveRewardAttempt(rewardId, "transaction_failed", "failed", e.getMessage());
            repository.updateRewardStatus(rewardId, "failed");
            repository.updateGroupEstado(request.groupId(), "failed");
            repository.saveAuditEvent(userId, "admin", "transaction_failed", request.groupId(), e.getMessage(), hashIp(ip));
            throw new BusinessException(ErrorCode.CONTRACT_ERROR, e.getMessage(), requestId);
        }
    }

    private void validateWallet(String wallet, String requestId) {
        if (wallet == null || wallet.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_WALLET, "Empty wallet address", requestId);
        }

        if (!wallet.startsWith("0x") || wallet.length() != 42) {
            throw new BusinessException(ErrorCode.INVALID_WALLET, "Invalid format: " + wallet, requestId);
        }

        String hex = wallet.substring(2);
        if (!hex.matches("[0-9a-fA-F]{40}")) {
            throw new BusinessException(ErrorCode.INVALID_WALLET, "Invalid hex characters: " + wallet, requestId);
        }

        if (wallet.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            throw new BusinessException(ErrorCode.INVALID_WALLET, "Zero address", requestId);
        }
    }

    private String generateRewardId(Long courseId, Long groupId, String idempotencyKey) {
        String input = courseId + ":" + groupId + ":" + idempotencyKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return "0x" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate rewardId", e);
        }
    }

    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return ip;
        }
    }
}
