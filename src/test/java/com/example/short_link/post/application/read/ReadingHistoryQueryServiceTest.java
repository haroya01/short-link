package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReadingHistoryQueryServiceTest {

  @Mock private PostReadRepository postReadRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;

  private ReadingHistoryQueryService service;

  @BeforeEach
  void setUp() {
    service = new ReadingHistoryQueryService(postReadRepository, postRepository, userRepository);
  }

  private PostEntity publishedPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostEntity draft(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "draft-" + id, "Draft", "ko");
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostReadEntity read(long postId) {
    return new PostReadEntity(9L, postId, Instant.parse("2026-01-01T00:00:00Z"));
  }

  private UserEntity user(long id, String username, boolean deleted) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    if (deleted) {
      ReflectionTestUtils.setField(u, "deletedAt", Instant.now());
    }
    return u;
  }

  @Test
  void listsEntriesNewestFirst() {
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 20))
        .thenReturn(List.of(read(5L), read(6L)));
    when(postReadRepository.countByUserId(9L)).thenReturn(2L);
    when(postRepository.findAllByIdIn(List.of(5L, 6L)))
        .thenReturn(List.of(publishedPost(5L, 2L), publishedPost(6L, 2L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "bob", false)));

    ReadingHistoryView view = service.list(9L, 0, 20);

    assertThat(view.hasNext()).isFalse();
    assertThat(view.items()).extracting(ReadingHistoryEntryView::postId).containsExactly(5L, 6L);
    assertThat(view.items().get(0).username()).isEqualTo("bob");
    assertThat(view.items().get(0).title()).isEqualTo("Title 5");
  }

  @Test
  void reportsHasNextWhenMorePages() {
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 20)).thenReturn(List.of(read(5L)));
    when(postReadRepository.countByUserId(9L)).thenReturn(25L); // (0+1)*20 < 25
    when(postRepository.findAllByIdIn(List.of(5L))).thenReturn(List.of(publishedPost(5L, 2L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "bob", false)));

    assertThat(service.list(9L, 0, 20).hasNext()).isTrue();
  }

  @Test
  void skipsUnpublishedPostsAndGoneAuthors() {
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 20))
        .thenReturn(List.of(read(5L), read(6L), read(7L)));
    when(postReadRepository.countByUserId(9L)).thenReturn(3L);
    when(postRepository.findAllByIdIn(List.of(5L, 6L, 7L)))
        .thenReturn(List.of(publishedPost(5L, 2L), draft(6L, 2L), publishedPost(7L, 3L)));
    // author 2 present, author 3 deleted -> post 7's row drops; post 6 (draft) drops too
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "bob", false), user(3L, "ghost", true)));

    ReadingHistoryView view = service.list(9L, 0, 20);

    assertThat(view.items()).extracting(ReadingHistoryEntryView::postId).containsExactly(5L);
  }

  @Test
  void emptyHistoryReturnsNoRows() {
    when(postReadRepository.findByUserIdOrderByReadAtDesc(9L, 0, 20)).thenReturn(List.of());
    when(postReadRepository.countByUserId(9L)).thenReturn(0L);

    ReadingHistoryView view = service.list(9L, 0, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.hasNext()).isFalse();
  }
}
