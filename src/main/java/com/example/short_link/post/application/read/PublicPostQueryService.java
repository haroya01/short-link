package com.example.short_link.post.application.read;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 없이 public 으로 접근 가능한 read service. PUBLISHED 글만 노출. UNPUBLISHED 는 410 Gone (작성자 의도적 제거),
 * DRAFT/SCHEDULED 는 404. 존재하지 않거나 soft-deleted user 도 404.
 *
 * <p>CTA_REF 블록은 content JSON 의 {@code ctaId} 를 lookup 해서 라이브러리 entity 의 label/url/style/purpose 까지
 * hydrate. 삭제된 CTA 는 deleted=true 로 표시 (UI 가 fallback 처리 가능).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;
  private final SeriesRepository seriesRepository;
  private final CtaRepository ctaRepository;
  private final ShortLinkUrlBuilder shortLinkUrlBuilder;

  public PublicPostListView listPublicPosts(String username) {
    UserEntity author = resolveAuthor(username);
    // Pinned posts (curation) surface first by pin_order; the rest keep publishedAt-desc. The repo
    // already returns publishedAt-desc and Stream.sorted is stable, so unpinned order is preserved.
    List<PublicPostListItem> posts =
        postRepository
            .findAllByUserIdAndStatusOrderByPublishedAtDesc(author.getId(), PostStatus.PUBLISHED)
            .stream()
            .sorted(PINNED_FIRST)
            .map(PublicPostListItem::from)
            .toList();
    return new PublicPostListView(PublicAuthorView.from(author), posts);
  }

  /**
   * Pinned (pin_order != null) before unpinned; pinned ordered by pin_order asc. Stable for ties.
   */
  private static final Comparator<PostEntity> PINNED_FIRST =
      (a, b) -> {
        Integer pa = a.getPinOrder();
        Integer pb = b.getPinOrder();
        if (pa != null && pb != null) return Integer.compare(pa, pb);
        if (pa != null) return -1;
        if (pb != null) return 1;
        return 0;
      };

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

    List<PostBlockEntity> entities =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId());
    Map<Long, CtaEntity> ctaMap = hydrateCtas(entities);
    List<PublicPostBlockView> blocks = new ArrayList<>(entities.size());
    for (PostBlockEntity entity : entities) {
      blocks.add(buildBlockView(entity, ctaMap));
    }

    return new PublicPostDetail(
        PublicAuthorView.from(author), PublicPostListItem.from(post), blocks, seriesNavFor(post));
  }

  /** Build the series nav for a published post, or null if it isn't in a series. */
  private PublicPostSeriesNav seriesNavFor(PostEntity post) {
    if (post.getSeriesId() == null) return null;
    SeriesEntity series = seriesRepository.findById(post.getSeriesId()).orElse(null);
    if (series == null) return null;
    List<PostEntity> siblings =
        postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED);
    int index = -1;
    for (int i = 0; i < siblings.size(); i++) {
      if (siblings.get(i).getId().equals(post.getId())) {
        index = i;
        break;
      }
    }
    if (index < 0) return null; // post not published in its own series — defensive
    PublicPostSeriesNav.NavLink prev = index > 0 ? navLink(siblings.get(index - 1)) : null;
    PublicPostSeriesNav.NavLink next =
        index < siblings.size() - 1 ? navLink(siblings.get(index + 1)) : null;
    return new PublicPostSeriesNav(
        series.getSlug(), series.getTitle(), index + 1, siblings.size(), prev, next);
  }

  private PublicPostSeriesNav.NavLink navLink(PostEntity post) {
    return new PublicPostSeriesNav.NavLink(post.getSlug(), post.getTitle());
  }

  private Map<Long, CtaEntity> hydrateCtas(List<PostBlockEntity> entities) {
    Map<Long, CtaEntity> ctaMap = new HashMap<>();
    for (PostBlockEntity entity : entities) {
      if (entity.getType() != PostBlockType.CTA_REF) continue;
      Long ctaId = parseCtaId(entity.getContent());
      if (ctaId == null || ctaMap.containsKey(ctaId)) continue;
      ctaRepository.findById(ctaId).ifPresent(c -> ctaMap.put(ctaId, c));
    }
    return ctaMap;
  }

  private PublicPostBlockView buildBlockView(PostBlockEntity entity, Map<Long, CtaEntity> ctaMap) {
    if (entity.getType() != PostBlockType.CTA_REF) {
      return PublicPostBlockView.from(entity);
    }
    Long ctaId = parseCtaId(entity.getContent());
    if (ctaId == null) {
      return PublicPostBlockView.from(entity);
    }
    CtaEntity cta = ctaMap.get(ctaId);
    if (cta == null) {
      // CTA 라이브러리에서 영구 삭제됐거나 (현재는 soft-delete 만 가능) 다른 user 의 CTA 면 lookup 실패.
      // 자기 글의 CTA 면 정상적으로 hydrate. 어쨌든 안전하게 null cta 로 반환 — UI 가 placeholder.
      return PublicPostBlockView.from(entity);
    }
    // Serve the tracked kurl short link when one exists, so clicks flow through the redirect and
    // attribute to this post. Falls back to the raw URL when tracking wasn't established.
    String url =
        cta.getTrackedShortCode() != null
            ? shortLinkUrlBuilder.build(ShortCode.of(cta.getTrackedShortCode()))
            : cta.getUrl();
    return PublicPostBlockView.fromWithCta(
        entity,
        new PublicPostBlockView.CtaInfo(
            cta.getLabel(), url, cta.getStyle().name(), cta.getPurpose().name(), cta.isDeleted()));
  }

  /** CTA_REF block content = JSON {"ctaId": N}. 파싱 실패 시 null. */
  private Long parseCtaId(String content) {
    if (content == null || content.isBlank()) return null;
    try {
      JsonNode node = OBJECT_MAPPER.readTree(content);
      JsonNode idNode = node.get("ctaId");
      if (idNode == null || !idNode.canConvertToLong()) return null;
      return idNode.asLong();
    } catch (Exception e) {
      return null;
    }
  }

  private UserEntity resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase();
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
