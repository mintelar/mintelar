package com.hilo.rewards.model;

import java.util.List;

public record RewardResponse(
    String rewardId,
    String status,
    String transactionHash,
    List<RecipientAmount> recipients
) {
    public record RecipientAmount(String wallet, String amount) {}
}
