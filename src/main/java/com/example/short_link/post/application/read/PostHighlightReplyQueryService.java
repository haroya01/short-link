package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHighlightReplyQueryService {

  private final PostHighlightReplyRepository replyRepository;
  private final PostHighlightRepository highlightRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public List<HighlightReplyView> listForHighlight(Long highlightId) {
    // 미발행 글의 하이라이트 답글은 공개 목록에 노출하지 않는다.
    Long postId =
        highlightRepository.findById(highlightId).map(PostHighlightEntity::getPostId).orElse(null);
    if (postId == null
        || postRepository.findById(postId).filter(PostEntity::isPublished).isEmpty()) {
      return List.of();
    }
    List<PostHighlightReplyEntity> replies =
        replyRepository.findAllByHighlightIdOrderByCreatedAtAsc(highlightId);
    List<Long> authorIds =
        replies.stream().map(PostHighlightReplyEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return replies.stream()
        .map(
            r -> {
              UserEntity author = authors.get(r.getUserId());
              return new HighlightReplyView(
                  r.getId(),
                  author == null ? null : PublicAuthorView.from(author),
                  r.getBody(),
                  r.getCreatedAt());
            })
        .toList();
  }
}
