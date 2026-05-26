package com.example.short_link.link.og.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OgOverrideServiceTest {

  @Autowired private OgOverrideService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void overrideWinsOverScrapedValues() {
    UserEntity user = userRepository.save(new UserEntity("og@example.com", "google", "g-og"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "ogtest1", user.getId(), null));
    link.applyOgMetadata("Scraped", "Scraped desc", "https://example.com/img.png", Instant.now());

    service.update(user.getId(), new ShortCode("ogtest1"), "Override title", null, null);

    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("ogtest1")).orElseThrow();
    assertThat(reloaded.getEffectiveOgTitle()).isEqualTo("Override title");
    assertThat(reloaded.getEffectiveOgDescription()).isEqualTo("Scraped desc");
    assertThat(reloaded.getEffectiveOgImage()).isEqualTo("https://example.com/img.png");
  }

  @Test
  void clearingOverrideFallsBackToScraped() {
    UserEntity user = userRepository.save(new UserEntity("og2@example.com", "google", "g-og2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "ogtest2", user.getId(), null));
    link.applyOgMetadata("S", "Sd", "Si", Instant.now());
    link.changeOgOverride("manual", null, null);

    service.update(user.getId(), new ShortCode("ogtest2"), "  ", null, null);

    LinkEntity reloaded = linkRepository.findByShortCode(new ShortCode("ogtest2")).orElseThrow();
    assertThat(reloaded.getOgTitleOverride()).isNull();
    assertThat(reloaded.getEffectiveOgTitle()).isEqualTo("S");
  }

  @Test
  void rejectsNonOwner() {
    UserEntity owner = userRepository.save(new UserEntity("o@example.com", "google", "g-o"));
    UserEntity other = userRepository.save(new UserEntity("x@example.com", "google", "g-x"));
    linkRepository.save(new LinkEntity("https://example.com", "ogtest3", owner.getId(), null));

    assertThatThrownBy(
            () -> service.update(other.getId(), new ShortCode("ogtest3"), "x", null, null))
        .isInstanceOf(LinkException.class);
  }
}
