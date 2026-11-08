package com.example.short_link.user.application.dto;

public record TwoFactorChallenge(Long userId, boolean restoresAccount) {}
