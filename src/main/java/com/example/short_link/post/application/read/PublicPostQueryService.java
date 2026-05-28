package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 없이 public 으로 접근 가능한 read service. PUBLISHED 글만 노출. UNPUBLISHED 는 410 Gone (작성자 의도적 제거),
 * DRAFT/SCHEDULED 는 404. 존재하지 않거나 soft-deleted user 도 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;

  public PublicPostListView listPublicPosts(String username) {
    UserEntity author = resolveAuthor(username);
    List<PublicPostListItem> posts =
        postRepository
            .findAllByUserIdAndStatusOrderByPublishedAtDesc(author.getId(), PostStatus.PUBLISHED)
            .stream()
            .map(PublicPostListItem::from)
            .toList();
    return new PublicPostListView(PublicAuthorView.from(author), posts);
  }

  public PublicPostDetail findPublicPost(String username, String slug) {
    UserEntity author = resolveAuthor(username);
    PostEntity post =
        postRepository
            .findByUserIdAndSlug(author.getId(), slug)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, slug));

    if (post.isUnpublished()) {
      throw new PostException(PostErrorCode.POST_GONE, slug);
    }
    if (!post.isPublished()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, slug);
    }

    List<PublicPostBlockView> blocks =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()).stream()
            .map(PublicPostBlockView::from)
            .toList();

    return new PublicPostDetail(
        PublicAuthorView.from(author), PublicPostListItem.from(post), blocks);
  }

  private UserEntity resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase();
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
