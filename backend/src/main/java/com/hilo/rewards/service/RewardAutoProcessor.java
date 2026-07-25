package com.hilo.rewards.service;

import com.hilo.rewards.exception.BusinessException;
import com.hilo.rewards.exception.ErrorCode;
import com.hilo.rewards.model.Group;
import com.hilo.rewards.model.RewardRequest;
import com.hilo.rewards.model.RewardResponse;
import com.hilo.rewards.repository.SupabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RewardAutoProcessor {

    private static final Logger log = LoggerFactory.getLogger(RewardAutoProcessor.class);

    private final SupabaseRepository repository;
    private final RewardService rewardService;

    public RewardAutoProcessor(SupabaseRepository repository, RewardService rewardService) {
        this.repository = repository;
        this.rewardService = rewardService;
    }

    @Scheduled(fixedDelayString = "${rewards.auto-process-interval-ms:30000}")
    public void processReadyGroups() {
        List<Group> readyGroups = repository.getGroupsByEstado("ready");

        if (readyGroups.isEmpty()) {
            return;
        }

        log.info("Found {} group(s) ready for reward distribution", readyGroups.size());

        for (Group group : readyGroups) {
            processGroup(group);
        }
    }

    private void processGroup(Group group) {
        String idempotencyKey = "auto-" + UUID.randomUUID();

        RewardRequest request = new RewardRequest(
            group.id(),
            group.courseId(),
            idempotencyKey
        );

        log.info("Auto-processing reward for group {} (course {})", group.id(), group.courseId());

        try {
            RewardResponse response = rewardService.processReward(request, "system-auto", "internal");
            log.info("Reward processed successfully: rewardId={}, txHash={}",
                response.rewardId(), response.transactionHash());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.REWARD_ALREADY_EXISTS) {
                log.info("Reward already exists for group {}, skipping", group.id());
            } else {
                log.error("Business error processing group {}: {}", group.id(), e.getMessage());
                repository.updateGroupEstado(group.id(), "failed");
            }
        } catch (Exception e) {
            log.error("Unexpected error processing group {}: {}", group.id(), e.getMessage());
            repository.updateGroupEstado(group.id(), "failed");
        }
    }
}
