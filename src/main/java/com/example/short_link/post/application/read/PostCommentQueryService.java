package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentLikeRepository;
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
  private final CommentLikeRepository commentLikeRepository;
  private final UserRepository userRepository;
  private final PostRepository postRepository;

  public List<CommentView> listForPost(Long postId) {
    // 다른 공개 read 와 일관되게, 발행 안 된 글 (초안/비공개) 의 댓글은 노출하지 않는다.
    if (postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return List.of();
    }
    List<CommentEntity> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
    List<Long> authorIds = comments.stream().map(CommentEntity::getUserId).distinct().toList();
    Map<Long, Long> likeCounts =
        commentLikeRepository.countByCommentIds(
            comments.stream().map(CommentEntity::getId).toList());
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
                    c.getCreatedAt(),
                    likeCounts.getOrDefault(c.getId(), 0L)))
        .toList();
  }

  /** The viewer's own comments across all posts — the "my comments" library. */
  public List<MyCommentView> listMyComments(Long userId) {
    List<CommentEntity> comments = commentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> postIds = comments.stream().map(CommentEntity::getPostId).distinct().toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<Long> authorIds = posts.values().stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    Map<Long, Long> likeCounts =
        commentLikeRepository.countByCommentIds(
            comments.stream().map(CommentEntity::getId).toList());

    return comments.stream()
        // 글이 사라지면(소프트 참조, FK 없음) 링크백할 맥락이 없으므로 제외한다.
        .filter(c -> posts.containsKey(c.getPostId()))
        .map(
            c -> {
              PostEntity post = posts.get(c.getPostId());
              UserEntity author = authors.get(post.getUserId());
              return new MyCommentView(
                  c.getId(),
                  c.getBody(),
                  c.getParentId(),
                  likeCounts.getOrDefault(c.getId(), 0L),
                  c.getCreatedAt(),
                  post.getSlug(),
                  post.getTitle(),
                  author == null ? null : author.getUsername());
            })
        .toList();
  }

  /** Of the post's comments, the ids the viewer liked — likedByMe for the authed reader. */
  public List<Long> likedCommentIds(Long userId, Long postId) {
    List<Long> ids =
        commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId).stream()
            .map(CommentEntity::getId)
            .toList();
    return commentLikeRepository.findLikedCommentIds(userId, ids);
  }

  private PublicAuthorView authorView(UserEntity user) {
    return user == null ? null : PublicAuthorView.from(user);
  }
}
