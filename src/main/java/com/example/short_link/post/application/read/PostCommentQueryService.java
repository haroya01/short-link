package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, unauthenticated comment listing for a post. Authors batch-hydrated. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentQueryService {

  private final CommentRepository commentRepository;
  private final UserRepository userRepository;
  private final PostRepository postRepository;

  public List<CommentView> listForPost(Long postId) {
    // 다른 공개 read 와 일관되게, 발행 안 된 글 (초안/비공개) 의 댓글은 노출하지 않는다.
    if (postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return List.of();
    }
    List<CommentEntity> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
    List<Long> authorIds = comments.stream().map(CommentEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return comments.stream()
        .map(
            c ->
                new CommentView(
                    c.getId(),
                    c.getParentId(),
                    authorView(authors.get(c.getUserId())),
                    c.getBody(),
                    c.getCreatedAt()))
        .toList();
  }

  private PublicAuthorView authorView(UserEntity user) {
    return user == null ? null : PublicAuthorView.from(user);
  }
}
