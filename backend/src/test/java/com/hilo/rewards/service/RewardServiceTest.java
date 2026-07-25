package com.hilo.rewards.service;

import com.hilo.rewards.exception.BusinessException;
import com.hilo.rewards.exception.ErrorCode;
import com.hilo.rewards.model.*;
import com.hilo.rewards.repository.SupabaseRepository;
import com.hilo.rewards.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    private SupabaseRepository repository;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private RateLimiter rateLimiter;

    private RewardService rewardService;

    private RewardRequest validRequest;
    private Group validGroup;
    private Course activeCourse;
    private GroupMember approvedMember1;
    private GroupMember approvedMember2;
    private Profile memberProfile1;
    private Profile memberProfile2;

    @BeforeEach
    void setUp() {
        rewardService = new RewardService(
            repository, blockchainService, rateLimiter,
            10, 18, "0x0000000000000000000000000000000000000000000000000000000000000001"
        );

        validRequest = new RewardRequest(100L, 1L, "idem-abc-123");
        validGroup = new Group(100L, "Test Group", 1L, "completed");
        activeCourse = new Course(1L, "Course 1", true);
        approvedMember1 = new GroupMember(1L, 100L, "user-1", true);
        approvedMember2 = new GroupMember(2L, 100L, "user-2", true);
        memberProfile1 = new Profile("user-1", "Alice", "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD0e", "student");
        memberProfile2 = new Profile("user-2", "Bob", "0x8Ba1f109551bD432803012645Ac136ddd64DBA72", "student");
    }

    @Test
    void processReward_successfulFlow() throws Exception {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1, approvedMember2));
        when(repository.getProfile("user-1")).thenReturn(memberProfile1);
        when(repository.getProfile("user-2")).thenReturn(memberProfile2);
        when(blockchainService.getBalance()).thenReturn(BigInteger.valueOf(1_000_000_000_000_000_000L));
        when(blockchainService.processReward(anyString(), anyList(), any(BigInteger.class)))
            .thenReturn("0xabc123");

        RewardResponse response = rewardService.processReward(validRequest, "admin-user", "127.0.0.1");

        assertEquals("submitted", response.status());
        assertNotNull(response.rewardId());
        assertEquals("0xabc123", response.transactionHash());
        assertEquals(2, response.recipients().size());

        verify(repository).saveReward(anyString(), eq(100L), eq("idem-abc-123"), isNull(), eq("processing"), any(BigDecimal.class));
        verify(blockchainService).processReward(anyString(), anyList(), any(BigInteger.class));
    }

    @Test
    void processReward_rateLimited_throwsException() throws Exception {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(false, 900));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
        verify(blockchainService, never()).processReward(anyString(), anyList(), any(BigInteger.class));
    }

    @Test
    void processReward_alreadyProcessed_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.REWARD_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void processReward_groupNotFound_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void processReward_courseInactive_throwsException() {
        Course inactiveCourse = new Course(1L, "Course 1", false);
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(inactiveCourse);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.COURSE_INACTIVE, ex.getErrorCode());
    }

    @Test
    void processReward_courseNotFound_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.COURSE_INACTIVE, ex.getErrorCode());
    }

    @Test
    void processReward_groupEmpty_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.GROUP_EMPTY, ex.getErrorCode());
    }

    @Test
    void processReward_memberNotApproved_throwsException() {
        GroupMember unapprovedMember = new GroupMember(1L, 100L, "user-1", false);
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(unapprovedMember));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.GROUP_NOT_COMPLETED, ex.getErrorCode());
    }

    @Test
    void processReward_memberWithoutWallet_throwsException() {
        Profile noWalletProfile = new Profile("user-1", "Alice", null, "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(noWalletProfile);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.MEMBER_WITHOUT_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_duplicateWallet_throwsException() {
        Profile duplicateWalletProfile = new Profile("user-2", "Bob", "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD0e", "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1, approvedMember2));
        when(repository.getProfile("user-1")).thenReturn(memberProfile1);
        when(repository.getProfile("user-2")).thenReturn(duplicateWalletProfile);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.DUPLICATE_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_insufficientGas_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(memberProfile1);
        when(blockchainService.getBalance()).thenReturn(BigInteger.ZERO);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.INSUFFICIENT_GAS, ex.getErrorCode());
    }

    @Test
    void processReward_blockchainFails_throwsException() throws Exception {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(memberProfile1);
        when(blockchainService.getBalance()).thenReturn(BigInteger.valueOf(1_000_000_000_000_000_000L));
        when(blockchainService.processReward(anyString(), anyList(), any(BigInteger.class)))
            .thenThrow(new RuntimeException("RPC error"));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.CONTRACT_ERROR, ex.getErrorCode());
        verify(repository).updateRewardStatus(anyString(), eq("failed"));
        verify(repository).updateGroupEstado(eq(100L), eq("failed"));
    }

    @Test
    void processReward_emptyWalletString_throwsException() {
        Profile emptyWalletProfile = new Profile("user-1", "Alice", "   ", "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(emptyWalletProfile);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.MEMBER_WITHOUT_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_zeroAddress_throwsException() {
        Profile zeroAddrProfile = new Profile("user-1", "Alice", "0x0000000000000000000000000000000000000000", "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(zeroAddrProfile);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.INVALID_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_invalidWalletLength_throwsException() {
        Profile shortWalletProfile = new Profile("user-1", "Alice", "0x123", "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(shortWalletProfile);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.INVALID_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_profileNotFound_throwsException() {
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(validGroup);
        when(repository.getCourse(1L)).thenReturn(activeCourse);
        when(repository.getGroupMembers(100L)).thenReturn(List.of(approvedMember1));
        when(repository.getProfile("user-1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(validRequest, "admin-user", "127.0.0.1"));

        assertEquals(ErrorCode.MEMBER_WITHOUT_WALLET, ex.getErrorCode());
    }
}
