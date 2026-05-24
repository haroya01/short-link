package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkNotOwnedException;
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
class LinkVisibilityServiceTest {

  @Autowired private LinkVisibilityService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void togglesStatsPublicForOwner() {
    UserEntity user = userRepository.save(new UserEntity("vis@example.com", "google", "g-vis"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "vis0001", user.getId(), null));

    assertThat(link.isStatsPublic()).isFalse();

    boolean nowPublic = service.setStatsPublic(user.getId(), "vis0001", true);
    assertThat(nowPublic).isTrue();

    boolean nowPrivate = service.setStatsPublic(user.getId(), "vis0001", false);
    assertThat(nowPrivate).isFalse();
  }

  @Test
  void rejectsNonOwner() {
    UserEntity owner = userRepository.save(new UserEntity("o@example.com", "google", "g-o"));
    UserEntity other = userRepository.save(new UserEntity("x@example.com", "google", "g-x"));
    linkRepository.save(new LinkEntity("https://example.com", "vis0002", owner.getId(), null));

    assertThatThrownBy(() -> service.setStatsPublic(other.getId(), "vis0002", true))
        .isInstanceOf(LinkNotOwnedException.class);
  }

  @Test
  void rejectsUnknownLink() {
    assertThatThrownBy(() -> service.setStatsPublic(1L, "missing1", true))
        .isInstanceOf(LinkNotFoundException.class);
  }
}
