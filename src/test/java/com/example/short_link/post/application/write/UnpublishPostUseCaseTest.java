package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnpublishPostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private ProfileCacheInvalidator cacheEviction;

  private UnpublishPostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UnpublishPostUseCase(postOwnership, postRepository, cacheEviction);
  }

  @Test
  void unpublishesPublished() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    post.publish();
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity result = useCase.execute(new UnpublishPostCommand(7L, 42L));

    assertThat(result.getStatus()).isEqualTo(PostStatus.UNPUBLISHED);
    assertThat(result.getPublishedAt()).isNotNull(); // preserved
  }

  @Test
  void rejectsUnpublishOfDraft() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    assertThatThrownBy(() -> useCase.execute(new UnpublishPostCommand(7L, 42L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.UNPUBLISH_NOT_PUBLISHED);
  }
}
