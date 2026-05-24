package com.example.short_link.user.presentation.response;

public record TwoFactorSetupResponse(String secret, String provisioningUri) {}
