package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
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
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostRevisionRepository postRevisionRepository;
  @Mock private PostOwnership postOwnership;

  private PostQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostQueryService(
            postRepository, postBlockRepository, postRevisionRepository, postOwnership);
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

  @Test
  void listBlocksReturnsViewsInOrder() {
    PostBlockEntity b1 = new PostBlockEntity(42L, PostBlockType.PARAGRAPH, "Hello", 0);
    PostBlockEntity b2 = new PostBlockEntity(42L, PostBlockType.IMAGE, "{\"url\":\"x\"}", 1);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L)).thenReturn(List.of(b1, b2));

    List<PostBlockView> views = service.listBlocks(7L, 42L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).type()).isEqualTo("PARAGRAPH");
    assertThat(views.get(0).content()).isEqualTo("Hello");
    assertThat(views.get(1).type()).isEqualTo("IMAGE");
    assertThat(views.get(1).blockOrder()).isEqualTo(1);
  }

  @Test
  void listBlocksEnforcesOwnership() {
    when(postOwnership.requireOwned(7L, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    assertThatThrownBy(() -> service.listBlocks(7L, 42L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }

  @Test
  void listRevisionsReturnsViews() {
    PostRevisionEntity r1 = new PostRevisionEntity(42L, 2, "Title v2", "{}");
    PostRevisionEntity r0 = new PostRevisionEntity(42L, 1, "Title v1", "{}");
    when(postRevisionRepository.findAllByPostIdOrderByVersionNumberDesc(42L))
        .thenReturn(List.of(r1, r0));

    List<PostRevisionView> views = service.listRevisions(7L, 42L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).versionNumber()).isEqualTo(2);
    assertThat(views.get(0).titleSnapshot()).isEqualTo("Title v2");
    assertThat(views.get(1).versionNumber()).isEqualTo(1);
  }
}
