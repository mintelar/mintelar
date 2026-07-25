package com.hilo.rewards.model;

public record Profile(
    String id,
    String fullName,
    String walletAddress,
    String role
) {}
