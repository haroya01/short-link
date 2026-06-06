package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishPostUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;
  @Mock private PostRevisionCapture postRevisionCapture;
  @Mock private ProfileCacheInvalidator cacheEviction;

  private PublishPostUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new PublishPostUseCase(postOwnership, postRepository, postRevisionCapture, cacheEviction);
  }

  @Test
  void publishesDraftAndCapturesRevision() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    PostEntity result = useCase.execute(new PublishPostCommand(7L, 42L));

    assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(result.getPublishedAt()).isNotNull();
    verify(postRevisionCapture).capture(result);
  }
}
