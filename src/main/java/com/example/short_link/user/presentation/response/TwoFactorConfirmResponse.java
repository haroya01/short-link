package com.example.short_link.user.presentation.response;

import java.util.List;

public record TwoFactorConfirmResponse(List<String> recoveryCodes) {}
