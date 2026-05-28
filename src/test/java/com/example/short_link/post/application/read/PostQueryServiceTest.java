package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

  @Mock private PostRepository postRepository;

  private PostQueryService service;

  @BeforeEach
  void setUp() {
    service = new PostQueryService(postRepository);
  }

  @Test
  void findOwnPostReturnsView() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    PostView view = service.findOwnPost(7L, 42L);

    assertThat(view.slug()).isEqualTo("my-post");
    assertThat(view.title()).isEqualTo("My Post");
    assertThat(view.status()).isEqualTo("DRAFT");
  }

  @Test
  void findOwnPostNotFound() {
    when(postRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findOwnPost(7L, 42L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void findOwnPostRejectsOtherOwner() {
    PostEntity post = new PostEntity(9L, "other-post", "Other", "ko");
    when(postRepository.findById(42L)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.findOwnPost(7L, 42L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void listMyPostsMapsAll() {
    PostEntity p1 = new PostEntity(7L, "post-1", "Post 1", "ko");
    PostEntity p2 = new PostEntity(7L, "post-2", "Post 2", "ja");
    when(postRepository.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(p1, p2));

    List<PostView> views = service.listMyPosts(7L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).slug()).isEqualTo("post-1");
    assertThat(views.get(1).slug()).isEqualTo("post-2");
  }

  @Test
  void listMyPostsEmptyWhenNone() {
    when(postRepository.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());

    assertThat(service.listMyPosts(7L)).isEmpty();
  }
}
