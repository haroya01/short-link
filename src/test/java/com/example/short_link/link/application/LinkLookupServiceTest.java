package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkLookupServiceTest {

  @Autowired private LinkLookupService service;
  @Autowired private LinkRepository repository;

  @Test
  void findsActiveOriginalUrl() {
    repository.save(new LinkEntity("https://example.com/dest", "lk00001"));

    String url = service.findActiveOriginalUrl("lk00001");

    assertThat(url).isEqualTo("https://example.com/dest");
  }

  @Test
  void throwsForExpiredLink() {
    repository.save(
        new LinkEntity(
            "https://example.com/old",
            "lk00002",
            null,
            Instant.now().minus(1, ChronoUnit.MINUTES)));

    assertThatThrownBy(() -> service.findActiveOriginalUrl("lk00002"))
        .isInstanceOf(LinkExpiredException.class);
  }

  @Test
  void throwsForUnknownCode() {
    assertThatThrownBy(() -> service.findActiveOriginalUrl("missing"))
        .isInstanceOf(LinkNotFoundException.class);
  }
}
