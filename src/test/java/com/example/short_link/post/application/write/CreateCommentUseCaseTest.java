package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateCommentUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private UserRepository userRepository;

  private CreateCommentUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateCommentUseCase(postRepository, commentRepository, userRepository);
  }

  private PostEntity publishedPost() {
    PostEntity p = new PostEntity(7L, "slug", "Title", "ko");
    p.publish();
    return p;
  }

  private UserEntity commenter() {
    UserEntity u = new UserEntity("c@x.com", "google", "g-9");
    u.claimUsername("carol");
    return u;
  }

  @Test
  void createsTopLevelComment() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost()));
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));

    CommentView c = useCase.execute(new CreateCommentCommand(9L, 42L, null, "  hello  "));

    assertThat(c.body()).isEqualTo("hello");
    assertThat(c.parentId()).isNull();
    assertThat(c.author().username()).isEqualTo("carol");
  }

  @Test
  void rejectsWhenPostNotPublished() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));

    assertThatThrownBy(() -> useCase.execute(new CreateCommentCommand(9L, 42L, null, "hi")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void rejectsReplyToReply() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost()));
    CommentEntity parentReply = new CommentEntity(42L, 1L, 5L, "i am a reply");
    when(commentRepository.findById(99L)).thenReturn(Optional.of(parentReply));

    assertThatThrownBy(() -> useCase.execute(new CreateCommentCommand(9L, 42L, 99L, "nested")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_PARENT_INVALID);
  }
}
