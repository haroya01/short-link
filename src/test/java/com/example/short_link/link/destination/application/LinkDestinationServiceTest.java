package com.example.short_link.link.destination.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
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
class LinkDestinationServiceTest {

  @Autowired
  private com.example.short_link.link.destination.application.write.AddDestinationUseCase
      addUseCase;

  @Autowired
  private com.example.short_link.link.destination.application.write.UpdateDestinationUseCase
      updateUseCase;

  @Autowired
  private com.example.short_link.link.destination.application.write.DeleteDestinationUseCase
      deleteUseCase;

  @Autowired
  private com.example.short_link.link.destination.application.read.LinkDestinationQueryService
      query;

  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void addListUpdateDelete() {
    UserEntity user = userRepository.save(new UserEntity("ab1@local.test", "google", "g-ab1"));
    linkRepository.save(new LinkEntity("https://example.com/ctrl", "ab11111", user.getId(), null));

    DestinationSummary v1 =
        addUseCase.execute(
            user.getId(), "ab11111", "https://example.com/A", 50, "variant-A", null, null, null);
    assertThat(v1.weight()).isEqualTo(50);
    assertThat(v1.label()).isEqualTo("variant-A");
    assertThat(v1.enabled()).isTrue();

    var list = query.list(user.getId(), "ab11111");
    assertThat(list).hasSize(1);

    var updated =
        updateUseCase.execute(
            user.getId(), "ab11111", v1.id(), null, 70, null, false, null, null, null);
    assertThat(updated.weight()).isEqualTo(70);
    assertThat(updated.enabled()).isFalse();

    deleteUseCase.execute(user.getId(), "ab11111", v1.id());
    assertThat(query.list(user.getId(), "ab11111")).isEmpty();
  }

  @Test
  void enforcesMaxPerLink() {
    UserEntity user = userRepository.save(new UserEntity("ab2@local.test", "google", "g-ab2"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab22222", user.getId(), null));

    for (int i = 0;
        i
            < com.example.short_link.link.destination.application.write.AddDestinationUseCase
                .MAX_PER_LINK;
        i++) {
      addUseCase.execute(
          user.getId(), "ab22222", "https://example.com/v" + i, 10, "v" + i, null, null, null);
    }
    assertThatThrownBy(
            () ->
                addUseCase.execute(
                    user.getId(), "ab22222", "https://example.com/v9", 10, "v9", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBadUrl() {
    UserEntity user = userRepository.save(new UserEntity("ab3@local.test", "google", "g-ab3"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab33333", user.getId(), null));

    assertThatThrownBy(
            () ->
                addUseCase.execute(
                    user.getId(), "ab33333", "javascript:alert(1)", 50, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clampsWeightToRange() {
    UserEntity user = userRepository.save(new UserEntity("ab4@local.test", "google", "g-ab4"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab44444", user.getId(), null));

    var huge =
        addUseCase.execute(
            user.getId(), "ab44444", "https://example.com/v", 999, null, null, null, null);
    assertThat(huge.weight())
        .isEqualTo(
            com.example.short_link.link.destination.application.write.AddDestinationUseCase
                .MAX_WEIGHT);

    var tiny =
        addUseCase.execute(
            user.getId(), "ab44444", "https://example.com/w", 0, null, null, null, null);
    assertThat(tiny.weight())
        .isEqualTo(
            com.example.short_link.link.destination.application.write.AddDestinationUseCase
                .MIN_WEIGHT);
  }

  @Test
  void cannotTouchAnotherUsersDestinations() {
    UserEntity owner = userRepository.save(new UserEntity("ab5@local.test", "google", "g-ab5"));
    UserEntity stranger =
        userRepository.save(new UserEntity("ab5b@local.test", "google", "g-ab5b"));
    linkRepository.save(new LinkEntity("https://example.com/c", "ab55555", owner.getId(), null));
    var v =
        addUseCase.execute(
            owner.getId(), "ab55555", "https://example.com/v", 50, null, null, null, null);

    assertThatThrownBy(() -> query.list(stranger.getId(), "ab55555"))
        .isInstanceOf(LinkException.class);
    assertThatThrownBy(() -> deleteUseCase.execute(stranger.getId(), "ab55555", v.id()))
        .isInstanceOf(LinkException.class);
  }
}
