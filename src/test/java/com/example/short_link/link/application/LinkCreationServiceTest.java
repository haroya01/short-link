package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkCreationServiceTest {

  @Autowired private LinkCreationService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void createsAnonymousLinkWithExpiry() {
    LinkCreated created = service.create("https://example.com/anon", null);

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getShortCode()).hasSize(7);
    assertThat(saved.getUserId()).isNull();
    assertThat(saved.getExpiresAt()).isNotNull();
  }

  @Test
  void createsAuthenticatedLinkWithoutExpiry() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-1"));

    LinkCreated created = service.create("https://example.com/owned", user.getId());

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getExpiresAt()).isNull();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    LinkCreated first = service.create("https://example.com", null);
    LinkCreated second = service.create("https://example.com", null);

    assertThat(first.shortCode()).isNotEqualTo(second.shortCode());
  }
}
