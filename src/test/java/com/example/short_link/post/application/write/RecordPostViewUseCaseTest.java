package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordPostViewUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostViewEventRepository postViewEventRepository;

  private RecordPostViewUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RecordPostViewUseCase(
            userRepository,
            postRepository,
            postViewEventRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private UserEntity author(String username) {
    UserEntity u = new UserEntity("u@x.com", "google", "g-1");
    u.claimUsername(username);
    return u;
  }

  @Test
  void incrementsViewCountAndAppendsEventForPublished() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new RecordPostViewCommand("john", "p"));

    assertThat(post.getViewCount()).isEqualTo(1L);
    verify(postRepository).save(post);
    ArgumentCaptor<PostViewEventEntity> event = ArgumentCaptor.forClass(PostViewEventEntity.class);
    verify(postViewEventRepository).save(event.capture());
    assertThat(event.getValue().getViewedAt()).isEqualTo(NOW);
  }

  @Test
  void normalizesUsername() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new RecordPostViewCommand("  JOHN  ", "p"));

    assertThat(post.getViewCount()).isEqualTo(1L);
  }

  @Test
  void noopForUnknownUser() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    useCase.execute(new RecordPostViewCommand("ghost", "p"));

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForDeletedUser() {
    UserEntity author = author("john");
    author.softDelete();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));

    useCase.execute(new RecordPostViewCommand("john", "p"));

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForDraftPost() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    // status DRAFT
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));

    useCase.execute(new RecordPostViewCommand("john", "p"));

    assertThat(post.getViewCount()).isZero();
    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForUnpublishedPost() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    post.unpublish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));

    useCase.execute(new RecordPostViewCommand("john", "p"));

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }
}
