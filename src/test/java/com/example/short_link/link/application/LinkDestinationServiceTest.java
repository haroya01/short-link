package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class LinkDestinationServiceTest {

  @Autowired private LinkDestinationService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void addListUpdateDelete() {
    UserEntity user = userRepository.save(new UserEntity("ab1@local.test", "google", "g-ab1"));
    linkRepository.save(new LinkEntity("https://example.com/ctrl", "ab11111", user.getId(), null));

    LinkDestinationService.DestinationSummary v1 =
        service.add(
            user.getId(), "ab11111", "https://example.com/A", 50, "variant-A", null, null, null);
    assertThat(v1.weight()).isEqualTo(50);
    assertThat(v1.label()).isEqualTo("variant-A");
    assertThat(v1.enabled()).isTrue();

    var list = service.list(user.getId(), "ab11111");
    assertThat(list).hasSize(1);

    var updated =
        service.update(user.getId(), "ab11111", v1.id(), null, 70, null, false, null, null, null);
    assertThat(updated.weight()).isEqualTo(70);
    assertThat(updated.enabled()).isFalse();

    service.delete(user.getId(), "ab11111", v1.id());
    assertThat(service.list(user.getId(), "ab11111")).isEmpty();
  }

  @Test
  void enforcesMaxPerLink() {
    UserEntity user = userRepository.save(new UserEntity("ab2@local.test", "google", "g-ab2"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab22222", user.getId(), null));

    for (int i = 0; i < LinkDestinationService.MAX_PER_LINK; i++) {
      service.add(
          user.getId(), "ab22222", "https://example.com/v" + i, 10, "v" + i, null, null, null);
    }
    assertThatThrownBy(
            () ->
                service.add(
                    user.getId(), "ab22222", "https://example.com/v9", 10, "v9", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBadUrl() {
    UserEntity user = userRepository.save(new UserEntity("ab3@local.test", "google", "g-ab3"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab33333", user.getId(), null));

    assertThatThrownBy(
            () ->
                service.add(
                    user.getId(), "ab33333", "javascript:alert(1)", 50, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clampsWeightToRange() {
    UserEntity user = userRepository.save(new UserEntity("ab4@local.test", "google", "g-ab4"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab44444", user.getId(), null));

    var huge =
        service.add(user.getId(), "ab44444", "https://example.com/v", 999, null, null, null, null);
    assertThat(huge.weight()).isEqualTo(LinkDestinationService.MAX_WEIGHT);

    var tiny =
        service.add(user.getId(), "ab44444", "https://example.com/w", 0, null, null, null, null);
    assertThat(tiny.weight()).isEqualTo(LinkDestinationService.MIN_WEIGHT);
  }

  @Test
  void cannotTouchAnotherUsersDestinations() {
    UserEntity owner = userRepository.save(new UserEntity("ab5@local.test", "google", "g-ab5"));
    UserEntity stranger =
        userRepository.save(new UserEntity("ab5b@local.test", "google", "g-ab5b"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab55555", owner.getId(), null));
    var v =
        service.add(owner.getId(), "ab55555", "https://example.com/v", 50, null, null, null, null);

    assertThatThrownBy(() -> service.list(stranger.getId(), "ab55555"))
        .isInstanceOf(LinkNotOwnedException.class);
    assertThatThrownBy(() -> service.delete(stranger.getId(), "ab55555", v.id()))
        .isInstanceOf(LinkNotOwnedException.class);
  }
}
