package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkProtectionServiceTest {

  @Autowired private LinkProtectionService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void setsAndVerifiesPassword() {
    UserEntity user = userRepository.save(new UserEntity("p@example.com", "google", "g-p"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "pw00001", user.getId(), null));

    service.update(user.getId(), "pw00001", "secret123", null);

    LinkEntity reloaded = linkRepository.findByShortCode("pw00001").orElseThrow();
    assertThat(reloaded.hasPassword()).isTrue();
    assertThat(service.checkPassword(reloaded, "secret123")).isTrue();
    assertThat(service.checkPassword(reloaded, "wrong")).isFalse();
  }

  @Test
  void emptyPasswordClearsHash() {
    UserEntity user = userRepository.save(new UserEntity("p2@example.com", "google", "g-p2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "pw00002", user.getId(), null));
    service.update(user.getId(), "pw00002", "x", null);
    service.update(user.getId(), "pw00002", "", null);
    LinkEntity reloaded = linkRepository.findByShortCode("pw00002").orElseThrow();
    assertThat(reloaded.hasPassword()).isFalse();
  }

  @Test
  void atomicViewIncrementStopsAtLimit() {
    UserEntity user = userRepository.save(new UserEntity("v@example.com", "google", "g-v"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "vw00001", user.getId(), null));
    service.update(user.getId(), "vw00001", null, 2);

    int first = linkRepository.incrementViewCountIfBelowLimit(link.getId());
    int second = linkRepository.incrementViewCountIfBelowLimit(link.getId());
    int third = linkRepository.incrementViewCountIfBelowLimit(link.getId());
    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(1);
    assertThat(third).isZero();
  }
}
