package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class LinkProtectionServiceTest {

  @Autowired private LinkProtectionService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void setsAndVerifiesPassword() {
    UserEntity user = userRepository.save(new UserEntity("p@example.com", "google", "g-p"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "pw00001", user.getId(), null));

    service.update(user.getId(), new ShortCode("pw00001"), "secret123", null);

    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("pw00001")).orElseThrow();
    assertThat(reloaded.hasPassword()).isTrue();
    assertThat(reloaded.getPasswordHash()).startsWith("$2a$10$");
    assertThat(service.checkPassword(reloaded, "secret123")).isTrue();
    assertThat(service.checkPassword(reloaded, "wrong")).isFalse();
    assertThat(service.checkPassword(reloaded, null)).isFalse();

    service.update(user.getId(), new ShortCode("pw00001"), null, 5);
    assertThat(service.checkPassword(reloaded, "secret123")).isTrue();
    assertThat(reloaded.getMaxViews()).isEqualTo(5);
  }

  @Test
  void emptyPasswordClearsHash() {
    UserEntity user = userRepository.save(new UserEntity("p2@example.com", "google", "g-p2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "pw00002", user.getId(), null));
    service.update(user.getId(), new ShortCode("pw00002"), "x", null);
    service.update(user.getId(), new ShortCode("pw00002"), "", null);
    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("pw00002")).orElseThrow();
    assertThat(reloaded.hasPassword()).isFalse();
    assertThat(service.checkPassword(reloaded, null)).isTrue();
  }

  @Test
  void atomicViewIncrementStopsAtLimit() {
    UserEntity user = userRepository.save(new UserEntity("v@example.com", "google", "g-v"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "vw00001", user.getId(), null));
    service.update(user.getId(), new ShortCode("vw00001"), null, 2);

    assertThat(linkRepository.spendView(link.linkId())).isTrue();
    assertThat(linkRepository.spendView(link.linkId())).isTrue();
    assertThat(linkRepository.spendView(link.linkId())).isFalse();
  }
}
