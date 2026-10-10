package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FollowStatusBlocksIntegrationTest {

  @Autowired private FollowQueryService followQuery;
  @Autowired private FollowUseCase followUseCase;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockRepository blockRepository;

  private long viewer;

  @BeforeEach
  void aViewerWhoBlockedOneAuthorAndWasBlockedByAnother() {
    viewer = user("fsb-viewer");
    long blocked = user("fsb-blocked");
    long blocker = user("fsb-blocker");
    user("fsb-neutral");
    blockRepository.save(new UserBlockEntity(viewer, blocked));
    blockRepository.save(new UserBlockEntity(blocker, viewer));
  }

  @Test
  void theStatusSaysWhichWayABlockRuns() {
    assertBlocks(followQuery.status(viewer, "fsb-blocked"), true, false);
    assertBlocks(followQuery.status(viewer, "fsb-blocker"), false, true);
    assertBlocks(followQuery.status(viewer, "fsb-neutral"), false, false);
  }

  @Test
  void anAnonymousReaderAndTheAuthorThemselfSeeNoBlocks() {
    assertBlocks(followQuery.status(null, "fsb-blocked"), false, false);
    assertBlocks(followQuery.status(null, "fsb-blocker"), false, false);
    assertBlocks(followQuery.status(viewer, "fsb-viewer"), false, false);
  }

  @Test
  void followAndUnfollowAnswerWithTheSameFlags() {
    assertBlocks(followUseCase.follow(viewer, "fsb-neutral", null), false, false);
    assertBlocks(followUseCase.unfollow(viewer, "fsb-neutral"), false, false);
    assertBlocks(followUseCase.unfollow(viewer, "fsb-blocked"), true, false);
    assertBlocks(followUseCase.unfollow(viewer, "fsb-blocker"), false, true);
    assertThatThrownBy(() -> followUseCase.follow(viewer, "fsb-blocker", null))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.BLOCKED_TARGET);
  }

  private static void assertBlocks(
      FollowStatus status, boolean blockedByViewer, boolean blocksViewer) {
    assertThat(status.blockedByViewer()).isEqualTo(blockedByViewer);
    assertThat(status.blocksViewer()).isEqualTo(blocksViewer);
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }
}
