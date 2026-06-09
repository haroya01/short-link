package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedPrefQueryServiceTest {

  @Mock private UserFeedPrefRepository repository;

  private FeedPrefQueryService service;

  @BeforeEach
  void setUp() {
    service = new FeedPrefQueryService(repository);
  }

  @Test
  void defaultsToRecentWhenUnset() {
    when(repository.findByUserId(9L)).thenReturn(Optional.empty());
    assertThat(service.get(9L).defaultTab()).isEqualTo("recent");
  }

  @Test
  void returnsStoredTab() {
    when(repository.findByUserId(9L))
        .thenReturn(Optional.of(new UserFeedPrefEntity(9L, "following")));
    assertThat(service.get(9L).defaultTab()).isEqualTo("following");
  }

  @Test
  void fallsBackWhenStoredTabNoLongerAllowed() {
    // A tab removed in a later release shouldn't strand the user on an invalid landing tab.
    when(repository.findByUserId(9L)).thenReturn(Optional.of(new UserFeedPrefEntity(9L, "legacy")));
    assertThat(service.get(9L).defaultTab()).isEqualTo("recent");
  }
}
