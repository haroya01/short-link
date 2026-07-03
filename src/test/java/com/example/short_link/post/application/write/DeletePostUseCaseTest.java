package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeletePostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostRevisionRepository postRevisionRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private PostLikeRepository postLikeRepository;
  @Mock private PostBookmarkRepository postBookmarkRepository;
  @Mock private PostHighlightRepository postHighlightRepository;
  @Mock private PostReadRepository postReadRepository;
  @Mock private ProfileCacheInvalidator cacheEviction;
  @Mock private CollectionConnectionCleaner connectionCleaner;

  private DeletePostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new DeletePostUseCase(
            postOwnership,
            postRepository,
            postBlockRepository,
            postRevisionRepository,
            commentRepository,
            postLikeRepository,
            postBookmarkRepository,
            postHighlightRepository,
            postReadRepository,
            cacheEviction,
            connectionCleaner);
  }

  @Test
  void deletesInCascadeOrder() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    ReflectionTestUtils.setField(post, "id", 42L);
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postHighlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(42L))
        .thenReturn(List.of(highlight(100L), highlight(101L)));

    useCase.execute(new DeletePostCommand(7L, 42L));

    InOrder order =
        inOrder(
            postBlockRepository,
            postRevisionRepository,
            commentRepository,
            postLikeRepository,
            postBookmarkRepository,
            connectionCleaner,
            postHighlightRepository,
            postReadRepository,
            postRepository);
    order.verify(postBlockRepository).deleteAllByPostId(42L);
    order.verify(postRevisionRepository).deleteAllByPostId(42L);
    order.verify(commentRepository).deleteAllByPostId(42L);
    order.verify(postLikeRepository).deleteAllByPostId(42L);
    order.verify(postBookmarkRepository).deleteAllByPostId(42L);
    // Highlight connections are purged with the ids read before the highlights are deleted.
    order.verify(connectionCleaner).purgeForHighlights(List.of(100L, 101L));
    order.verify(postHighlightRepository).deleteAllByPostId(42L);
    order.verify(postReadRepository).deleteAllByPostId(42L);
    // The post's own connections go before the post row.
    order.verify(connectionCleaner).purgeForPost(42L);
    order.verify(postRepository).delete(post);
  }

  private static PostHighlightEntity highlight(long id) {
    PostHighlightEntity h = new PostHighlightEntity(42L, 7L, 0, 0, 0, 3, "abc", null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  @Test
  void rejectsForeignOwner() {
    when(postOwnership.requireOwned(7L, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    assertThatThrownBy(() -> useCase.execute(new DeletePostCommand(7L, 42L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }
}
