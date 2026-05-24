package com.example.short_link.link.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkWebhookRegisterRequest(
    @NotBlank @Size(max = 2048) String url, @Size(max = 100) String name) {}
