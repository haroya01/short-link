package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    when(follows.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

    assertThat(reader.follows(1L, 2L)).isTrue();
    assertThat(reader.follows(2L, 1L)).isFalse();
  }
}
