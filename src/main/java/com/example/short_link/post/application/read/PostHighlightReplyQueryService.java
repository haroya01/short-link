package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, unauthenticated reply listing for a highlight's flat thread. Authors batch-hydrated. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostHighlightReplyQueryService {

  private final PostHighlightReplyRepository replyRepository;
  private final UserRepository userRepository;

  public List<HighlightReplyView> listForHighlight(Long highlightId) {
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
