package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostHighlightQueryServiceTest {

  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;

  private PostHighlightQueryService service;

  @BeforeEach
  void setUp() {
    service = new PostHighlightQueryService(highlightRepository, postRepository, userRepository);
  }

  private PostEntity publishedPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostHighlightEntity highlight(long id, long postId, long userId) {
    PostHighlightEntity h = new PostHighlightEntity(postId, userId, 0, 0, 3, "quote-" + id);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listForPostAttributesHighlightsAndNullsMissingAuthor() {
    when(postRepository.findById(5L)).thenReturn(Optional.of(publishedPost(5L, 1L)));
    when(highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(5L))
        .thenReturn(List.of(highlight(10L, 5L, 1L), highlight(11L, 5L, 999L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));

    List<HighlightView> views = service.listForPost(5L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).author().username()).isEqualTo("alice");
    assertThat(views.get(1).author()).isNull(); // user 999 not found
  }

  @Test
  void listForPostEmptyWhenPostNotPublished() {
    PostEntity draft = new PostEntity(1L, "draft", "Draft", "ko");
    ReflectionTestUtils.setField(draft, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(draft));

    assertThat(service.listForPost(5L)).isEmpty();
  }

  @Test
  void listMineCarriesPostRefAndNullsWhenPostOrAuthorMissing() {
    // hA: post present + author present | hB: post missing | hC: post present + author missing
    when(highlightRepository.findAllByUserIdOrderByCreatedAtDesc(1L))
        .thenReturn(
            List.of(highlight(10L, 5L, 1L), highlight(11L, 999L, 1L), highlight(12L, 6L, 1L)));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(publishedPost(5L, 2L), publishedPost(6L, 3L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "bob")));

    List<MyHighlightView> views = service.listMine(1L);

    assertThat(views).hasSize(3);
    MyHighlightView present = views.get(0);
    assertThat(present.postUsername()).isEqualTo("bob");
    assertThat(present.postSlug()).isEqualTo("slug-5");
    assertThat(present.postTitle()).isEqualTo("Title 5");

    MyHighlightView postMissing = views.get(1);
    assertThat(postMissing.postUsername()).isNull();
    assertThat(postMissing.postSlug()).isNull();
    assertThat(postMissing.postTitle()).isNull();

    MyHighlightView authorMissing = views.get(2);
    assertThat(authorMissing.postUsername()).isNull(); // author 3 not found
    assertThat(authorMissing.postSlug()).isEqualTo("slug-6");
  }
}
