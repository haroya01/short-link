package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
    @NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 16) String platform) {}
