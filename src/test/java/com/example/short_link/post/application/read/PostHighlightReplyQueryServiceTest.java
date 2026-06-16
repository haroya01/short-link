package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostHighlightReplyQueryServiceTest {

  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private UserRepository userRepository;

  private PostHighlightReplyQueryService service;

  @BeforeEach
  void setUp() {
    service = new PostHighlightReplyQueryService(replyRepository, userRepository);
  }

  private PostHighlightReplyEntity reply(long id, long userId, String body) {
    PostHighlightReplyEntity r = new PostHighlightReplyEntity(50L, userId, body);
    ReflectionTestUtils.setField(r, "id", id);
    return r;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listForHighlightAttributesRepliesAndNullsMissingAuthor() {
    when(replyRepository.findAllByHighlightIdOrderByCreatedAtAsc(50L))
        .thenReturn(List.of(reply(10L, 1L, "동의해요"), reply(11L, 999L, "익명 답글")));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "alice")));

    List<HighlightReplyView> views = service.listForHighlight(50L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).author().username()).isEqualTo("alice");
    assertThat(views.get(0).body()).isEqualTo("동의해요");
    assertThat(views.get(1).author()).isNull(); // user 999 not found
  }
}
