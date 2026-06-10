package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
import com.example.short_link.post.domain.repository.CommentRepository;
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
class PostCommentQueryServiceTest {

  @Mock private CommentRepository commentRepository;
  @Mock private CommentLikeRepository commentLikeRepository;
  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;

  private PostCommentQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new PostCommentQueryService(
            commentRepository, commentLikeRepository, userRepository, postRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private PostEntity publishedPost(long id) {
    PostEntity p = new PostEntity(1L, "slug", "Title", "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  @Test
  void listsCommentsWithAuthorsAndParentId() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost(42L)));
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

  @Test
  void hidesCommentsOfUnpublishedOrMissingPost() {
    when(postRepository.findById(42L)).thenReturn(Optional.empty());
    assertThat(service.listForPost(42L)).isEmpty();
  }

  @Test
  void listHydratesLikeCountsInOneBatch() {
    org.mockito.Mockito.when(postRepository.findById(5L))
        .thenReturn(java.util.Optional.of(publishedPost(5L)));
    com.example.short_link.post.domain.CommentEntity c =
        new com.example.short_link.post.domain.CommentEntity(5L, 1L, null, "hi");
    org.springframework.test.util.ReflectionTestUtils.setField(c, "id", 11L);
    org.mockito.Mockito.when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(5L))
        .thenReturn(java.util.List.of(c));
    org.mockito.Mockito.when(userRepository.findAllByIdIn(java.util.List.of(1L)))
        .thenReturn(java.util.List.of(user(1L, "kim")));
    org.mockito.Mockito.when(commentLikeRepository.countByCommentIds(java.util.List.of(11L)))
        .thenReturn(java.util.Map.of(11L, 4L));

    java.util.List<CommentView> views = service.listForPost(5L);

    org.assertj.core.api.Assertions.assertThat(views).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(views.get(0).likeCount()).isEqualTo(4L);
  }

  @Test
  void likedCommentIdsScopesToThePostsComments() {
    com.example.short_link.post.domain.CommentEntity c =
        new com.example.short_link.post.domain.CommentEntity(5L, 1L, null, "hi");
    org.springframework.test.util.ReflectionTestUtils.setField(c, "id", 11L);
    org.mockito.Mockito.when(commentRepository.findAllByPostIdOrderByCreatedAtAsc(5L))
        .thenReturn(java.util.List.of(c));
    org.mockito.Mockito.when(commentLikeRepository.findLikedCommentIds(9L, java.util.List.of(11L)))
        .thenReturn(java.util.List.of(11L));

    org.assertj.core.api.Assertions.assertThat(service.likedCommentIds(9L, 5L))
        .containsExactly(11L);
  }
}
