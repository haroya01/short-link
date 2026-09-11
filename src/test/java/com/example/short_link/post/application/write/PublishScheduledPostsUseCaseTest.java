package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishScheduledPostsUseCaseTest {
  @Mock private PostRepository posts;
  @Mock private PublishScheduledPostUseCase publishPost;

  @Test
  void countsOnlyCommittedPublicationsAndContinuesAfterAFailedPost() {
    Instant now = Instant.now();
    when(posts.findScheduledDueIds(now)).thenReturn(List.of(1L, 2L, 3L));
    when(publishPost.execute(1L, now)).thenThrow(new IllegalStateException("commit failed"));
    when(publishPost.execute(2L, now)).thenReturn(false);
    when(publishPost.execute(3L, now)).thenReturn(true);

    assertThat(new PublishScheduledPostsUseCase(posts, publishPost).execute(now)).isEqualTo(1);

    verify(publishPost).execute(3L, now);
  }

  @Test
  void emptyWorkListDoesNotOpenPublicationTransactions() {
    Instant now = Instant.now();
    when(posts.findScheduledDueIds(now)).thenReturn(List.of());

    assertThat(new PublishScheduledPostsUseCase(posts, publishPost).execute(now)).isZero();

    verifyNoInteractions(publishPost);
  }
}
