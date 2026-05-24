package com.example.short_link.user.api.response;

public record TwoFactorSetupResponse(String secret, String provisioningUri) {}
