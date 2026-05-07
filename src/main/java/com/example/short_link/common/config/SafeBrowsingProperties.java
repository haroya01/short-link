package com.example.short_link.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.safe-browsing")
public record SafeBrowsingProperties(
    boolean enabled, String apiKey, Duration cacheTtl, Duration timeout) {}
