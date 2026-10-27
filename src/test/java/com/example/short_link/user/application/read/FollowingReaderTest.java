package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.repository.FollowRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowingReaderTest {

  private final FollowRepository follows = mock(FollowRepository.class);
  private final FollowingReader reader = new FollowingReader(follows);

  @Test
  void readsWhoTheFollowerFollows() {
    when(follows.findFollowingIds(1L)).thenReturn(List.of(2L, 3L));

    assertThat(reader.followingIds(1L)).containsExactly(2L, 3L);
  }

  @Test
  void asksTheEdgeInTheFollowersDirection() {
    when(follows.exists(FollowEdge.follower(1L).following(2L))).thenReturn(true);

    assertThat(reader.follows(FollowEdge.follower(1L).following(2L))).isTrue();
    assertThat(reader.follows(FollowEdge.follower(2L).following(1L))).isFalse();
  }
}
