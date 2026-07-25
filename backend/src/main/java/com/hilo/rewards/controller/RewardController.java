package com.hilo.rewards.controller;

import com.hilo.rewards.exception.BusinessException;
import com.hilo.rewards.exception.ErrorCode;
import com.hilo.rewards.model.RewardRequest;
import com.hilo.rewards.model.RewardResponse;
import com.hilo.rewards.service.RewardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private static final Logger log = LoggerFactory.getLogger(RewardController.class);

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping("/process")
    public ResponseEntity<RewardResponse> processReward(
            @Valid @RequestBody RewardRequest request,
            HttpServletRequest httpRequest) {

        String userId = (String) httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");
        String requestId = (String) httpRequest.getAttribute("requestId");
        String ip = getClientIp(httpRequest);

        log.info("POST /api/v1/rewards/process - userId={}, groupId={}, requestId={}",
            userId, request.groupId(), requestId);

        RewardResponse response = rewardService.processReward(request, userId, ip);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
