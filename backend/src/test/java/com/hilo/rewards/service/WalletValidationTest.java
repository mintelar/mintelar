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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletValidationTest {

    @Mock
    private SupabaseRepository repository;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private RateLimiter rateLimiter;

    private RewardService rewardService;

    private Method validateWalletMethod;

    @BeforeEach
    void setUp() throws Exception {
        rewardService = new RewardService(
            repository, blockchainService, rateLimiter,
            10, 18, "0x0000000000000000000000000000000000000000000000000000000000000001"
        );
        validateWalletMethod = RewardService.class.getDeclaredMethod("validateWallet", String.class, String.class);
        validateWalletMethod.setAccessible(true);
    }

    private void invokeValidateWallet(String wallet, String requestId) throws Exception {
        validateWalletMethod.invoke(rewardService, wallet, requestId);
    }

    @Test
    void validateWallet_validAddress_noException() throws Exception {
        invokeValidateWallet("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD0e", "req-1");
    }

    @Test
    void validateWallet_validAddress2_noException() throws Exception {
        invokeValidateWallet("0x8Ba1f109551bD432803012645Ac136ddd64DBA72", "req-1");
    }

    @Test
    void validateWallet_emptyString_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_blankString_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("   ", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_null_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet(null, "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_noHexPrefix_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("742d35Cc6634C0532925a3b844Bc9e7595f2bD0e", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_tooShort_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("0x123", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_tooLong_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD0eextra", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_zeroAddress_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("0x0000000000000000000000000000000000000000", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_zeroAddressMixedCase_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("0x0000000000000000000000000000000000000000", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_invalidChecksumChars_throwsInvalidWallet() {
        Exception ex = assertThrows(Exception.class,
            () -> invokeValidateWallet("0xZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", "req-1"));
        assertTrue(ex.getCause() instanceof BusinessException);
        assertEquals(ErrorCode.INVALID_WALLET, ((BusinessException) ex.getCause()).getErrorCode());
    }

    @Test
    void validateWallet_lowercaseAddress_noException() throws Exception {
        invokeValidateWallet("0x742d35cc6634c0532925a3b844bc9e7595f2bd0e", "req-1");
    }

    @Test
    void validateWallet_uppercaseAddress_noException() throws Exception {
        invokeValidateWallet("0x742D35CC6634C0532925A3B844BC9E7595F2BD0E", "req-1");
    }

    @Test
    void processReward_invalidWalletInGroup_throwsException() {
        Profile invalidProfile = new Profile("user-1", "Alice", "0xINVALID", "student");
        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(new Group(100L, "Test", 1L, "completed"));
        when(repository.getCourse(1L)).thenReturn(new Course(1L, "Course", true));
        when(repository.getGroupMembers(100L)).thenReturn(List.of(new GroupMember(1L, 100L, "user-1", true)));
        when(repository.getProfile("user-1")).thenReturn(invalidProfile);

        RewardRequest request = new RewardRequest(100L, 1L, "idem-1");
        BusinessException ex = assertThrows(BusinessException.class,
            () -> rewardService.processReward(request, "admin", "127.0.0.1"));

        assertEquals(ErrorCode.INVALID_WALLET, ex.getErrorCode());
    }

    @Test
    void processReward_validWalletAddresses_completes() throws Exception {
        Profile validProfile1 = new Profile("user-1", "Alice", "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD0e", "student");
        Profile validProfile2 = new Profile("user-2", "Bob", "0x8Ba1f109551bD432803012645Ac136ddd64DBA72", "student");
        GroupMember m1 = new GroupMember(1L, 100L, "user-1", true);
        GroupMember m2 = new GroupMember(2L, 100L, "user-2", true);

        when(rateLimiter.checkRateLimit(anyString(), anyString(), anyLong()))
            .thenReturn(new RateLimiter.RateLimitResult(true, 0));
        when(repository.isRewardProcessedOnChain(anyString())).thenReturn(false);
        when(repository.getGroup(100L)).thenReturn(new Group(100L, "Test", 1L, "completed"));
        when(repository.getCourse(1L)).thenReturn(new Course(1L, "Course", true));
        when(repository.getGroupMembers(100L)).thenReturn(List.of(m1, m2));
        when(repository.getProfile("user-1")).thenReturn(validProfile1);
        when(repository.getProfile("user-2")).thenReturn(validProfile2);
        when(blockchainService.getBalance()).thenReturn(BigInteger.valueOf(1_000_000_000_000_000_000L));
        when(blockchainService.processReward(anyString(), anyList(), any(BigInteger.class)))
            .thenReturn("0xdef456");

        RewardRequest request = new RewardRequest(100L, 1L, "idem-2");
        RewardResponse response = rewardService.processReward(request, "admin", "127.0.0.1");

        assertEquals("submitted", response.status());
        verify(blockchainService).processReward(anyString(), anyList(), any(BigInteger.class));
    }
}
