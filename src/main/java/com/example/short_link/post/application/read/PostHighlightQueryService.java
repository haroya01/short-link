package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
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

/** Highlight reads — public attributed list per post, and the viewer's own library. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHighlightQueryService {

  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;

  /** Public, attributed highlights on a published post (Medium-style social highlights). */
  public List<HighlightView> listForPost(Long postId) {
    if (postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return List.of();
    }
    List<PostHighlightEntity> highlights =
        highlightRepository.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(postId);
    List<Long> userIds =
        highlights.stream().map(PostHighlightEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> users =
        userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    Map<Long, Long> replyCounts =
        replyRepository.countByHighlightIds(
            highlights.stream().map(PostHighlightEntity::getId).toList());

    return highlights.stream()
        .map(
            h -> {
              UserEntity user = users.get(h.getUserId());
              return new HighlightView(
                  h.getId(),
                  user == null ? null : PublicAuthorView.from(user),
                  h.getBlockOrder(),
                  h.getEndBlockOrder(),
                  h.getStartOffset(),
                  h.getEndOffset(),
                  h.getQuote(),
                  h.getCreatedAt(),
                  h.getNote(),
                  replyCounts.getOrDefault(h.getId(), 0L));
            })
        .toList();
  }

  /** The viewer's own highlights across all posts — the "my highlights" library. */
  public List<MyHighlightView> listMine(Long userId) {
    List<PostHighlightEntity> highlights =
        highlightRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> postIds =
        highlights.stream().map(PostHighlightEntity::getPostId).distinct().toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<Long> authorIds = posts.values().stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return highlights.stream()
        .map(
            h -> {
              PostEntity post = posts.get(h.getPostId());
              UserEntity author = post == null ? null : authors.get(post.getUserId());
              return new MyHighlightView(
                  h.getId(),
                  h.getQuote(),
                  h.getBlockOrder(),
                  h.getEndBlockOrder(),
                  author == null ? null : author.getUsername(),
                  post == null ? null : post.getSlug(),
                  post == null ? null : post.getTitle(),
                  h.getCreatedAt(),
                  h.getNote());
            })
        .toList();
  }
}
