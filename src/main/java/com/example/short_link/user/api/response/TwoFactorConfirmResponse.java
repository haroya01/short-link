package com.example.short_link.user.api.response;

import java.util.List;

public record TwoFactorConfirmResponse(List<String> recoveryCodes) {}
