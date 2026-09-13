package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Batch-loads authors and drops deleted-author posts, preserving the input order. */
@Component
@RequiredArgsConstructor
public class PostFeedItemAssembler {

  private final UserRepository userRepository;

  public List<PublicFeedItem> assemble(List<PostEntity> posts) {
    List<Long> authorIds = posts.stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    return posts.stream()
        .filter(p -> authors.containsKey(p.getUserId()))
        .map(p -> toItem(p, authors.get(p.getUserId())))
        .toList();
  }

  public PublicFeedItem toItem(PostEntity post, UserEntity author) {
    return new PublicFeedItem(
        post.getId(),
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
