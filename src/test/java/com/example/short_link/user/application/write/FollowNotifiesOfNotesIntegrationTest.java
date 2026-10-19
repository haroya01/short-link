package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FollowNotifiesOfNotesIntegrationTest {

  @Autowired private FollowUseCase follows;
  @Autowired private FollowRequestUseCase followRequests;
  @Autowired private NotificationFollowerReader followers;
  @Autowired private UserRepository userRepository;

  private UserEntity writer;
  private long fan;
  private long other;

  private UserEntity user(String handle, boolean locked) {
    UserEntity u = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    u.claimUsername(handle);
    u.updateLocked(locked);
    return userRepository.save(u);
  }

  @BeforeEach
  void setUp() {
    fan = user("fnn-fan", false).getId();
    other = user("fnn-other", false).getId();
  }

  @Test
  void aNewFollowHearsOfNewNotesUntilTheBellIsTurnedOff() {
    writer = user("fnn-writer", false);

    assertThat(follows.follow(fan, writer.getUsername(), null).notifyNotes()).isTrue();
    assertThat(followers.noteSubscribersOf(writer.getId())).containsExactly(fan);

    follows.setNoteNotifications(fan, writer.getUsername(), false);
    assertThat(followers.noteSubscribersOf(writer.getId())).isEmpty();
    assertThat(follows.follow(fan, writer.getUsername(), null).notifyNotes()).isFalse();
  }

  @Test
  void anApprovedRequestHearsOfNewNotesAsAnOpenFollowDoes() {
    writer = user("fnn-locked", true);
    follows.follow(fan, writer.getUsername(), null);
    follows.follow(other, writer.getUsername(), null);
    assertThat(followers.noteSubscribersOf(writer.getId())).isEmpty();

    followRequests.authorize(writer.getId(), "fnn-fan");
    assertThat(followers.noteSubscribersOf(writer.getId())).containsExactly(fan);

    followRequests.onUnlocked(new AccountUnlockedEvent(writer.getId()));
    assertThat(followers.noteSubscribersOf(writer.getId())).containsExactlyInAnyOrder(fan, other);
  }
}
