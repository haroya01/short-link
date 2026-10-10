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
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicPostQueryService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;
  private final SeriesRepository seriesRepository;
  private final SeriesItemReader seriesItemReader;
  private final CtaRepository ctaRepository;
  private final ShortLinkUrlBuilder shortLinkUrlBuilder;
  private final Clock clock;

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

  // 토큰이 곧 접근 권한이라 로그인은 보지 않는다. 발행 글은 공개 읽기가 보여 줄 때만, 초안·예약 글은 내려지지 않았고 작성자가 쓸 수 있을 때만 본문을 주고, 나머지는
  // 모두 404다. 비공개 글은 시리즈 탐색에 포함하지 않는다.
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
    if (!previewable(post, author)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, "");
    }
    return buildDetail(author, post);
  }

  private boolean previewable(PostEntity post, UserEntity author) {
    if (post.isPublished()) {
      return true;
    }
    return (post.isDraft() || post.isScheduled())
        && !post.isTakenDown()
        && author.canWriteAt(clock.instant());
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
    List<SeriesEntry> items = seriesItemReader.readableEntries(series.getId());
    List<SeriesEntry> posts = items.stream().filter(e -> e.type() == SeriesItemType.POST).toList();
    int index = indexOf(posts, post.getId());
    int itemIndex = indexOf(items, post.getId());
    if (index < 0 || itemIndex < 0) return null;
    return new PublicPostSeriesNav(
        series.getSlug(),
        series.getTitle(),
        index + 1,
        posts.size(),
        index > 0 ? navLink(posts.get(index - 1)) : null,
        index < posts.size() - 1 ? navLink(posts.get(index + 1)) : null,
        itemIndex + 1,
        items.size(),
        itemIndex > 0 ? itemLink(items.get(itemIndex - 1)) : null,
        itemIndex < items.size() - 1 ? itemLink(items.get(itemIndex + 1)) : null);
  }

  private static int indexOf(List<SeriesEntry> entries, Long postId) {
    for (int i = 0; i < entries.size(); i++) {
      SeriesEntry entry = entries.get(i);
      if (entry.type() == SeriesItemType.POST && entry.refId().equals(postId)) return i;
    }
    return -1;
  }

  private static PublicPostSeriesNav.NavLink navLink(SeriesEntry post) {
    return new PublicPostSeriesNav.NavLink(post.slug(), post.title());
  }

  private static PublicPostSeriesNav.ItemLink itemLink(SeriesEntry entry) {
    return entry.type() == SeriesItemType.POST
        ? new PublicPostSeriesNav.ItemLink(entry.type().name(), entry.slug(), null, entry.title())
        : new PublicPostSeriesNav.ItemLink(entry.type().name(), null, entry.refId(), entry.title());
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
