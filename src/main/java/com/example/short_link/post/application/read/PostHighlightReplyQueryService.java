package com.example.short_link.post.application.read;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.HighlightReplyLikeRepository;
import com.example.short_link.post.domain.repository.HighlightReplyLikeRepository.Likes;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
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

  private static final Likes NO_LIKES = new Likes(0, false);

  private final PostHighlightReplyRepository replyRepository;
  private final HighlightReplyLikeRepository likeRepository;
  private final PostHighlightRepository highlightRepository;
  private final PostRepository postRepository;
  private final UserSummaryReader userSummaries;
  private final CommentMentions mentions;
  private final PostAccessPolicy access;

  public PostScoped<List<HighlightReplyView>> listForHighlight(
      Long highlightId, PostReader reader) {
    PostHighlightEntity highlight = highlightRepository.findById(highlightId).orElse(null);
    PostEntity post =
        highlight == null ? null : postRepository.findById(highlight.getPostId()).orElse(null);
    if (post == null
        || !access.readable(post, reader)
        || !access.hears(highlight.getRoomId(), reader.viewerId())) {
      return new PostScoped<>(List.of(), false);
    }
    return new PostScoped<>(
        replies(highlightId, reader.viewerId()), !PostAccessPolicy.isOpen(post));
  }

  private List<HighlightReplyView> replies(Long highlightId, Long viewerId) {
    List<PostHighlightReplyEntity> replies =
        replyRepository.findHeardByHighlightId(highlightId, viewerId);
    List<Long> authorIds =
        replies.stream().map(PostHighlightReplyEntity::getUserId).distinct().toList();
    Map<Long, UserSummary> authors =
        userSummaries.findAll(authorIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    Function<String, List<String>> mentioned =
        mentions.in(replies.stream().map(PostHighlightReplyEntity::getBody).toList());
    Map<Long, Likes> likes =
        likeRepository.likesOf(
            replies.stream().map(PostHighlightReplyEntity::getId).toList(), viewerId);

    return replies.stream()
        .map(
            r -> {
              UserSummary author = authors.get(r.getUserId());
              Likes liked = likes.getOrDefault(r.getId(), NO_LIKES);
              return new HighlightReplyView(
                  r.getId(),
                  author == null ? null : PublicAuthorView.from(author),
                  r.getBody(),
                  r.getCreatedAt(),
                  mentioned.apply(r.getBody()),
                  liked.count(),
                  liked.likedByViewer());
            })
        .toList();
  }
}
