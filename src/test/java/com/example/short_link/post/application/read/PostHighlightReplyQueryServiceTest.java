package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
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
class PostHighlightReplyQueryServiceTest {

  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;

  private PostHighlightReplyQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostHighlightReplyQueryService(
            replyRepository, highlightRepository, postRepository, userRepository);
  }

  private PostHighlightReplyEntity reply(long id, long userId, String body) {
    PostHighlightReplyEntity r = new PostHighlightReplyEntity(50L, userId, body);
    ReflectionTestUtils.setField(r, "id", id);
    return r;
  }

  private PostHighlightEntity highlight(long id, long postId) {
    PostHighlightEntity h = new PostHighlightEntity(postId, 1L, 0, 0, 0, 3, "인용", null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private PostEntity post(long id, boolean published) {
    PostEntity p = new PostEntity(1L, "slug-" + id, "Title " + id, "ko");
    if (published) p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listForHighlightAttributesRepliesAndNullsMissingAuthor() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(50L, 60L)));
    when(postRepository.findById(60L)).thenReturn(Optional.of(post(60L, true)));
    when(replyRepository.findAllByHighlightIdOrderByCreatedAtAsc(50L))
        .thenReturn(List.of(reply(10L, 1L, "동의해요"), reply(11L, 999L, "익명 답글")));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));

    List<HighlightReplyView> views = service.listForHighlight(50L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).author().username()).isEqualTo("alice");
    assertThat(views.get(0).body()).isEqualTo("동의해요");
    assertThat(views.get(1).author()).isNull(); // user 999 not found
  }

  // 원문이 미발행(초안·비공개·관리자 차단)이면 하이라이트 답글 스레드를 통째로 숨긴다 — 열거 가능한 highlightId 로 새지 않게.
  @Test
  void emptyWhenParentPostUnpublished() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(50L, 60L)));
    when(postRepository.findById(60L)).thenReturn(Optional.of(post(60L, false)));

    assertThat(service.listForHighlight(50L)).isEmpty();
    verifyNoInteractions(replyRepository);
  }

  // 없는 하이라이트도 조용히 빈 목록 — 존재 여부가 새지 않게.
  @Test
  void emptyWhenHighlightMissing() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.empty());

    assertThat(service.listForHighlight(50L)).isEmpty();
    verifyNoInteractions(replyRepository);
  }
}
