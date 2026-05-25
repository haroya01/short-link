package com.example.short_link.link.access.presentation.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LinkProtectionRequest(@Size(max = 200) String password, @Min(1) Integer maxViews) {}
