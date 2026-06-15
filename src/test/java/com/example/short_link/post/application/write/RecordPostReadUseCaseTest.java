package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecordPostReadUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private PostReadRepository postReadRepository;

  private RecordPostReadUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RecordPostReadUseCase(postRepository, postReadRepository);
  }

  private PostEntity publishedPost(long id) {
    PostEntity p = new PostEntity(1L, "slug-" + id, "Title", "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  @Test
  void recordCreatesNewEntryForPublishedPost() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L)));
    when(postReadRepository.findByUserIdAndPostId(9L, 5L)).thenReturn(Optional.empty());

    useCase.record(9L, 5L);

    ArgumentCaptor<PostReadEntity> captor = ArgumentCaptor.forClass(PostReadEntity.class);
    verify(postReadRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(9L);
    assertThat(captor.getValue().getPostId()).isEqualTo(5L);
  }

  @Test
  void recordTouchesExistingEntry() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L)));
    Instant old = Instant.parse("2020-01-01T00:00:00Z");
    PostReadEntity existing = new PostReadEntity(9L, 5L, old);
    when(postReadRepository.findByUserIdAndPostId(9L, 5L)).thenReturn(Optional.of(existing));

    useCase.record(9L, 5L);

    verify(postReadRepository).save(existing);
    assertThat(existing.getReadAt()).isAfter(old);
  }

  @Test
  void recordIgnoresMissingPost() {
    when(postRepository.findById(5L)).thenReturn(Optional.empty());

    useCase.record(9L, 5L);

    verify(postReadRepository, never()).save(any());
  }

  @Test
  void recordIgnoresUnpublishedPost() {
    PostEntity draft = new PostEntity(1L, "draft", "Draft", "ko");
    ReflectionTestUtils.setField(draft, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(draft));

    useCase.record(9L, 5L);

    verify(postReadRepository, never()).save(any());
  }

  @Test
  void removeForgetsOnePost() {
    useCase.remove(9L, 5L);
    verify(postReadRepository).deleteByUserIdAndPostId(9L, 5L);
  }

  @Test
  void clearWipesHistory() {
    useCase.clear(9L);
    verify(postReadRepository).deleteByUserId(9L);
  }
}
