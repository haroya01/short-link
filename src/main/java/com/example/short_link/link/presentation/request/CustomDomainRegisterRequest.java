package com.example.short_link.link.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomDomainRegisterRequest(@NotBlank @Size(max = 253) String domain) {}
