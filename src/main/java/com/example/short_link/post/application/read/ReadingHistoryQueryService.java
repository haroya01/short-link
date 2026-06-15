package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostReadRepository;
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
 * The caller's reading history, most recently read first. Stale entries (post deleted / unpublished
 * or author gone) are skipped, so a cleared-up library never shows dead rows. Posts and authors are
 * batch-loaded to avoid an N+1 over the page.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingHistoryQueryService {

  private final PostReadRepository postReadRepository;
  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public ReadingHistoryView list(Long userId, int page, int size) {
    List<PostReadEntity> reads =
        postReadRepository.findByUserIdOrderByReadAtDesc(userId, page, size);
    boolean hasNext = (long) (page + 1) * size < postReadRepository.countByUserId(userId);
    return new ReadingHistoryView(hydrate(reads), page, size, hasNext);
  }

  private List<ReadingHistoryEntryView> hydrate(List<PostReadEntity> reads) {
    if (reads.isEmpty()) {
      return List.of();
    }
    List<Long> postIds = reads.stream().map(PostReadEntity::getPostId).toList();
    Map<Long, PostEntity> posts =
        postRepository.findAllByIdIn(postIds).stream()
            .filter(PostEntity::isPublished)
            .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors =
        userRepository
            .findAllByIdIn(posts.values().stream().map(PostEntity::getUserId).distinct().toList())
            .stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return reads.stream()
        .filter(r -> posts.containsKey(r.getPostId()))
        .filter(r -> authors.containsKey(posts.get(r.getPostId()).getUserId()))
        .map(
            r -> {
              PostEntity post = posts.get(r.getPostId());
              UserEntity author = authors.get(post.getUserId());
              return new ReadingHistoryEntryView(
                  post.getId(),
                  author.getUsername(),
                  author.getAvatarUrl(),
                  post.getTitle(),
                  post.getSlug(),
                  post.getExcerpt(),
                  post.getOgImageUrl(),
                  r.getReadAt());
            })
        .toList();
  }
}
