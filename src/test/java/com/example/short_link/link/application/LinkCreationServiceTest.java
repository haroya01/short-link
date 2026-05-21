package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class LinkCreationServiceTest {

  @Autowired private LinkCreationService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void createsAnonymousLinkWithOneDayExpiry() {
    LinkCreated created = service.create("https://example.com/anon", null, null, null);

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getShortCode()).hasSize(7);
    assertThat(saved.getUserId()).isNull();
    assertThat(saved.getExpiresAt())
        .isBetween(Instant.now().plusSeconds(86000), Instant.now().plusSeconds(87000));
  }

  @Test
  void createsAuthenticatedLinkWithoutExpiry() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-1"));

    LinkCreated created = service.create("https://example.com/owned", user.getId(), null, null);

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getExpiresAt()).isNull();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    LinkCreated first = service.create("https://example.com", null, null, null);
    LinkCreated second = service.create("https://example.com", null, null, null);

    assertThat(first.shortCode()).isNotEqualTo(second.shortCode());
  }

  @Test
  void createsWithCustomCodeForAuthenticatedUser() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-2"));

    LinkCreated created =
        service.create("https://example.com/custom", user.getId(), "myLink", null);

    assertThat(created.shortCode()).isEqualTo("myLink");
    LinkEntity saved = linkRepository.findByShortCode("myLink").orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
  }

  @Test
  void throwsDuplicateForExistingCustomCode() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-3"));
    service.create("https://example.com/first", user.getId(), "taken1", null);

    assertThatThrownBy(
            () -> service.create("https://example.com/second", user.getId(), "taken1", null))
        .isInstanceOf(DuplicateShortCodeException.class);
  }

  @Test
  void ignoresCustomCodeForAnonymousUser() {
    LinkCreated created = service.create("https://example.com", null, "ignored", null);

    assertThat(created.shortCode()).isNotEqualTo("ignored");
    assertThat(created.shortCode()).hasSize(7);
  }

  @Test
  void acceptsRequestedExpiresAtForAuthenticatedUser() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-4"));
    Instant requested =
        Instant.now().plus(Duration.ofDays(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    LinkCreated created = service.create("https://example.com", user.getId(), null, requested);

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getExpiresAt()).isEqualTo(requested);
  }

  @Test
  void ignoresRequestedExpiresAtForAnonymousUser() {
    Instant requested = Instant.now().plus(Duration.ofDays(30));

    LinkCreated created = service.create("https://example.com", null, null, requested);

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getExpiresAt())
        .isBetween(Instant.now().plusSeconds(86000), Instant.now().plusSeconds(87000));
  }
}
