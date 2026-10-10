package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuePreviewTokenUseCaseTest {

  @Mock private UserModerationGuard moderation;
  @Mock private PostOwnership postOwnership;
  @Mock private PostRepository postRepository;

  private IssuePreviewTokenUseCase useCase() {
    return new IssuePreviewTokenUseCase(moderation, postOwnership, postRepository);
  }

  @Test
  void issueGeneratesAndPersistsTokenForOwnedPost() {
    PostEntity post = new PostEntity(7L, "p", "P", "ko");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    String token = useCase().issue(7L, 42L);

    assertThat(token).isNotBlank();
    assertThat(post.getPreviewToken()).isEqualTo(token);
    verify(postRepository).save(post);
  }

  @Test
  void issueIsIdempotentReturningTheExistingToken() {
    PostEntity post = new PostEntity(7L, "p", "P", "ko");
    post.ensurePreviewToken("existing-token");
    when(postOwnership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    String token = useCase().issue(7L, 42L);

    assertThat(token).isEqualTo("existing-token");
  }
}
