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
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 조회: PUBLISHED만 노출하고 UNPUBLISHED는 410, DRAFT/SCHEDULED·삭제 작성자는 404다. 삭제된 CTA 참조는 deleted=true로
 * 반환한다.
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
    // 안정 정렬이므로 고정되지 않은 글은 저장소의 발행 최신순을 유지한다.
    List<PublicPostListItem> posts =
        postRepository
            .findAllByUserIdAndStatusOrderByPublishedAtDesc(author.getId(), PostStatus.PUBLISHED)
            .stream()
            .sorted(PINNED_FIRST)
            .map(PublicPostListItem::from)
            .toList();
    return new PublicPostListView(PublicAuthorView.from(author), posts);
  }

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

    return buildDetail(author, post);
  }

  /** 미리보기 토큰 자체가 접근 권한이므로 로그인·공개 상태 검사를 생략한다. 없는 토큰과 삭제 작성자는 모두 404이며, 비공개 글은 시리즈 탐색에 포함하지 않는다. */
  public PublicPostDetail findPreviewPost(String token) {
    if (token == null || token.isBlank()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, "");
    }
    PostEntity post =
        postRepository
            .findByPreviewToken(token)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, ""));
    UserEntity author =
        userRepository
            .findById(post.getUserId())
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, ""));
    return buildDetail(author, post);
  }

  private PublicPostDetail buildDetail(UserEntity author, PostEntity post) {
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
      // 삭제됐거나 다른 작성자의 CTA는 빈 참조로 반환한다.
      return PublicPostBlockView.from(entity);
    }
    // 추적 링크가 있으면 이 글의 클릭으로 집계하고, 없으면 원본 URL로 이동한다.
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
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
