package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicFeedQueryServiceTest {

  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;

  private PublicFeedQueryService service;

  @BeforeEach
  void setUp() {
    service = new PublicFeedQueryService(postRepository, userRepository, followRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private PostEntity post(long userId, String slug) {
    return new PostEntity(userId, slug, "Title " + slug, "ko");
  }

  @Test
  void recentFeedMapsAuthorsAndExcludesMissingAuthor() {
    PostEntity p1 = post(1L, "a");
    PostEntity p2 = post(2L, "b"); // author 2 not returned (deleted/missing) → excluded
    when(postRepository.findPublishedRecent(0, 20)).thenReturn(List.of(p1, p2));
    when(userRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished()).thenReturn(1L);

    PublicFeedView view = service.feed("recent", 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.items().get(0).author().username()).isEqualTo("alice");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void hasNextWhenMorePagesRemain() {
    when(postRepository.findPublishedRecent(0, 2))
        .thenReturn(List.of(post(1L, "a"), post(1L, "b")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished()).thenReturn(10L);

    PublicFeedView view = service.feed("recent", 0, 2);

    assertThat(view.items()).hasSize(2);
    assertThat(view.hasNext()).isTrue();
  }

  @Test
  void feedByTagUsesTagQuery() {
    when(postRepository.findPublishedByTag("spring", 0, 20)).thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublishedByTag("spring")).thenReturn(1L);

    PublicFeedView view = service.feedByTag("spring", 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).slug()).isEqualTo("a");
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void followingFeedReturnsPostsFromFollowedAuthors() {
    when(postRepository.findPublishedByAuthorIds(List.of(2L, 3L), 0, 20))
        .thenReturn(List.of(post(2L, "a")));
    when(userRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(user(2L, "bob")));
    when(postRepository.countPublishedByAuthorIds(List.of(2L, 3L))).thenReturn(1L);
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of(2L, 3L));

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).hasSize(1);
    assertThat(view.items().get(0).author().username()).isEqualTo("bob");
  }

  @Test
  void followingFeedIsEmptyWhenUserFollowsNoOne() {
    when(followRepository.findFollowingIds(9L)).thenReturn(List.of());

    PublicFeedView view = service.feedFollowing(9L, 0, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void popularTagsDelegatesToRepository() {
    when(postRepository.findPopularTags(50))
        .thenReturn(
            List.of(
                new com.example.short_link.post.domain.TagCount("spring", 7L),
                new com.example.short_link.post.domain.TagCount("react", 3L)));

    var tags = service.popularTags(50);

    assertThat(tags).hasSize(2);
    assertThat(tags.get(0).tag()).isEqualTo("spring");
    assertThat(tags.get(0).count()).isEqualTo(7);
  }

  @Test
  void trendingUsesTrendingQuery() {
    when(postRepository.findPublishedTrending(0, 20)).thenReturn(List.of(post(1L, "a")));
    when(userRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(user(1L, "alice")));
    when(postRepository.countPublished()).thenReturn(1L);

    service.feed("trending", 0, 20);

    verify(postRepository).findPublishedTrending(0, 20);
  }
}
