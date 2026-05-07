package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RecentErrorsServiceTest {

  private final Logger log = LoggerFactory.getLogger(RecentErrorsServiceTest.class);
  private RecentErrorsBuffer buffer;
  private RecentErrorsService service;

  @BeforeEach
  void setup() {
    buffer = new RecentErrorsBuffer(50);
    buffer.install();
    service = new RecentErrorsService(buffer);
  }

  @AfterEach
  void teardown() {
    buffer.uninstall();
  }

  @Test
  void usesDefaultLimitWhenNullOrNonPositive() {
    for (int i = 0; i < 3; i++) log.error("e{}", i);
    assertThat(service.recent(null)).hasSizeGreaterThanOrEqualTo(3);
    assertThat(service.recent(0)).hasSizeGreaterThanOrEqualTo(3);
    assertThat(service.recent(-5)).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void respectsExplicitLimit() {
    for (int i = 0; i < 10; i++) log.error("e{}", i);
    assertThat(service.recent(2)).hasSize(2);
  }
}
