package com.example.short_link.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "short-link.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingConfig {}
