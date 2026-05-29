package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
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

/**
 * Global public feed across all authors — the velog-style home where anyone browses published
 * posts. Only PUBLISHED posts from non-deleted authors are exposed. Authors are batch-hydrated to
 * avoid per-post N+1.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicFeedQueryService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public PublicFeedView feed(String sort, int page, int size) {
    List<PostEntity> posts =
        "trending".equalsIgnoreCase(sort)
            ? postRepository.findPublishedTrending(page, size)
            : postRepository.findPublishedRecent(page, size);

    List<Long> authorIds = posts.stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    List<PublicFeedItem> items =
        posts.stream()
            .filter(p -> authors.containsKey(p.getUserId()))
            .map(p -> toItem(p, authors.get(p.getUserId())))
            .toList();

    long total = postRepository.countPublished();
    boolean hasNext = (long) (page + 1) * size < total;
    return new PublicFeedView(items, page, size, hasNext);
  }

  private PublicFeedItem toItem(PostEntity post, UserEntity author) {
    return new PublicFeedItem(
        PublicAuthorView.from(author),
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getPublishedAt(),
        post.getViewCount(),
        post.getLikeCount());
  }
}
