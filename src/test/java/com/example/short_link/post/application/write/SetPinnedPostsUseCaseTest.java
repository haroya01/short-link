package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetPinnedPostsUseCaseTest {

  @Mock private PostRepository postRepository;

  private SetPinnedPostsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetPinnedPostsUseCase(postRepository);
  }

  private PostEntity published(long id, String slug) {
    PostEntity p = new PostEntity(7L, slug, slug, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  @Test
  void setsPinOrderByListIndexAndClearsTheRest() {
    PostEntity p1 = published(1L, "a");
    PostEntity p2 = published(2L, "b");
    PostEntity p3 = published(3L, "c");
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(7L, PostStatus.PUBLISHED))
        .thenReturn(List.of(p1, p2, p3));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(7L, List.of(3L, 1L));

    assertThat(p3.getPinOrder()).isEqualTo(0);
    assertThat(p1.getPinOrder()).isEqualTo(1);
    assertThat(p2.getPinOrder()).isNull();
  }

  @Test
  void ignoresIdsNotAmongTheAuthorsPublishedPosts() {
    PostEntity p1 = published(1L, "a");
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(7L, PostStatus.PUBLISHED))
        .thenReturn(List.of(p1));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(7L, List.of(99L)); // not owned / not published

    assertThat(p1.getPinOrder()).isNull();
  }

  @Test
  void nullListClearsAllPins() {
    PostEntity p1 = published(1L, "a");
    p1.pinAt(0);
    when(postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(7L, PostStatus.PUBLISHED))
        .thenReturn(List.of(p1));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(7L, null);

    assertThat(p1.getPinOrder()).isNull();
  }
}
