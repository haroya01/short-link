package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
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
class PostCommentQueryServiceTest {

  @Mock private CommentRepository commentRepository;
  @Mock private UserRepository userRepository;

  private PostCommentQueryService service;

  @BeforeEach
  void setUp() {
    service = new PostCommentQueryService(commentRepository, userRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listsCommentsWithAuthorsAndParentId() {
    CommentEntity top = new CommentEntity(42L, 1L, null, "first");
    CommentEntity reply = new CommentEntity(42L, 2L, 10L, "reply");
    when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(42L)).thenReturn(List.of(top, reply));
    when(userRepository.findAllByIdIn(List.of(1L, 2L)))
        .thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));

    List<CommentView> views = service.listForPost(42L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).author().username()).isEqualTo("alice");
    assertThat(views.get(0).body()).isEqualTo("first");
    assertThat(views.get(1).parentId()).isEqualTo(10L);
    assertThat(views.get(1).author().username()).isEqualTo("bob");
  }
}
