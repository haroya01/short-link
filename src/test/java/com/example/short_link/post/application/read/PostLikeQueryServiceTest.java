package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostLikeEntity;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostLikeQueryServiceTest {

  @Mock private PostRepository postRepository;
  @Mock private PostLikeRepository postLikeRepository;
  @Mock private UserRepository userRepository;

  private PostLikeQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostLikeQueryService(
            postRepository, postLikeRepository, new PostFeedItemAssembler(userRepository));
  }

  private PostEntity publishedPost(long id, long authorId, String slug) {
    PostEntity p = new PostEntity(authorId, slug, "Title " + slug, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private UserEntity author(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void statusReflectsLikeCountAndLiked() {
    PostEntity post = publishedPost(42L, 100L, "p");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));
    when(postLikeRepository.existsByPostIdAndUserId(42L, 9L)).thenReturn(true);

    PostLikeStatus status = service.status(9L, 42L);

    assertThat(status.liked()).isTrue();
  }

  @Test
  void likedPostsHydratesInLikeOrderAndSkipsStale() {
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L))
        .thenReturn(
            List.of(
                new PostLikeEntity(1L, 9L),
                new PostLikeEntity(2L, 9L),
                new PostLikeEntity(3L, 9L)));
    PostEntity p1 = publishedPost(1L, 100L, "a");
    PostEntity p3 = publishedPost(3L, 100L, "c");
    PostEntity p2 = new PostEntity(100L, "b", "B", "ko"); // DRAFT → filtered
    ReflectionTestUtils.setField(p2, "id", 2L);
    when(postRepository.findAllByIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(p1, p2, p3));
    when(userRepository.findAllByIdIn(List.of(100L))).thenReturn(List.of(author(100L, "alice")));

    List<PublicFeedItem> list = service.likedPosts(9L);

    assertThat(list).extracting(PublicFeedItem::id).containsExactly(1L, 3L);
    assertThat(list.get(0).author().username()).isEqualTo("alice");
    assertThat(list.get(0).slug()).isEqualTo("a");
  }

  @Test
  void likedPostsEmptyWhenNone() {
    when(postLikeRepository.findAllByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
    assertThat(service.likedPosts(9L)).isEmpty();
  }
}
