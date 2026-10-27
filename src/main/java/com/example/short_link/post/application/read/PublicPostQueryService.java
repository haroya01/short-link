package com.example.short_link.post.application.read;

import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.cta.application.read.CtaCard;
import com.example.short_link.cta.application.read.CtaCards;
import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.application.access.PostUnlistedUrls;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
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
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
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

  private final UserSummaryReader userSummaries;
  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;
  private final SeriesRepository seriesRepository;
  private final SeriesItemReader seriesItemReader;
  private final CtaCards ctaCards;
  private final UserBlockChecker userBlocks;
  private final PostAccessPolicy access;
  private final PostUnlistedUrls unlistedUrls;
  private final PostRooms rooms;
  private final UserModerationGuard moderation;

  public PostScoped<PublicPostListView> listPublicPosts(String username, PostReader reader) {
    UserSummary author = resolveAuthor(username);
    BlockRelation blocks = userBlocks.between(reader.viewerId(), author.id());
    PostAccessPolicy.Lists lists = access.forLists(reader);
    List<PublicPostListItem> posts = new ArrayList<>();
    boolean restricted = false;
    if (!blocks.any()) {
      // 안정 정렬이므로 고정되지 않은 글은 저장소의 발행 최신순을 유지한다.
      for (PostEntity post :
          postRepository.findProfilePosts(author.id(), reader.viewerId()).stream()
              .sorted(PINNED_FIRST)
              .toList()) {
        PostAccess decided = lists.of(post);
        if (decided.hidden()) continue;
        restricted |= !PostAccessPolicy.isOpen(post);
        posts.add(PublicPostListItem.of(post, decided, ownerView(post, author, reader)));
      }
    }
    return new PostScoped<>(
        new PublicPostListView(
            PublicAuthorView.from(author), posts, blocks.blockedByViewer(), blocks.blocksViewer()),
        restricted);
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

  public PostScoped<PublicPostDetail> findPublicPost(
      String username, String slug, PostReader reader, String acceptLanguage) {
    UserSummary author = resolveAuthor(username);
    PostEntity post =
        postRepository
            .findUnblockedByUserIdAndSlug(author.id(), slug, reader.viewerId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, slug));

    if (post.isUnpublished() && access.revealsExistence(post, reader)) {
      throw new PostException(PostErrorCode.POST_GONE, slug);
    }
    PostAccess decided = access.decide(post, reader);
    if (decided.hidden()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, slug);
    }
    return new PostScoped<>(
        detail(author, post, decided, reader, acceptLanguage), !PostAccessPolicy.isOpen(post));
  }

  public PostScoped<PublicPostDetail> findReadablePost(
      String username, String slug, PostReader reader) {
    PostScoped<PublicPostDetail> found = findPublicPost(username, slug, reader, null);
    PostLockView locked = found.body().post().locked();
    if (locked != null) {
      throw new PostException(PostErrorCode.POST_LOCKED, slug).with("reason", locked.reason());
    }
    return found;
  }

  private PublicPostDetail detail(
      UserSummary author,
      PostEntity post,
      PostAccess decided,
      PostReader reader,
      String acceptLanguage) {
    OwnerAudience owner = ownerView(post, author, reader);
    if (decided.full()) {
      PublicPostListItem item = PublicPostListItem.of(post, decided, owner);
      PostRoomView room = rooms.of(post);
      return buildDetail(author, post, room == null ? item : item.in(room));
    }
    return new PublicPostDetail(
        PublicAuthorView.from(author),
        PublicPostListItem.of(post, decided, owner),
        List.of(
            new PublicPostBlockView(
                PostBlockType.PARAGRAPH.name(),
                PostLockedNotice.text(decided.lock(), acceptLanguage),
                0,
                null)),
        null);
  }

  private OwnerAudience ownerView(PostEntity post, UserSummary author, PostReader reader) {
    if (reader.viewerId() == null || !post.isOwnedBy(reader.viewerId())) return null;
    return OwnerAudience.of(post, unlistedUrls.of(post, author.username()));
  }

  // 토큰이 곧 접근 권한이라 로그인은 보지 않는다. 발행 글은 공개 읽기가 보여 줄 때만, 초안·예약 글은 내려지지 않았고 작성자가 쓸 수 있을 때만 본문을 주고, 나머지는
  // 모두 404다. 비공개 글은 시리즈 탐색에 포함하지 않는다.
  public PostScoped<PublicPostDetail> findPreviewPost(String token, PostReader reader) {
    if (token == null || token.isBlank()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, "");
    }
    PostEntity post =
        postRepository
            .findByPreviewToken(token)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, ""));
    UserSummary author =
        userSummaries
            .findActive(post.getUserId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, ""));
    if (post.isDraft() || post.isScheduled()) {
      if (post.isTakenDown() || !moderation.canWrite(author.id())) {
        throw new PostException(PostErrorCode.POST_NOT_FOUND, "");
      }
      return new PostScoped<>(
          buildDetail(author, post, PublicPostListItem.of(post, PostAccess.FULL, null)), true);
    }
    PostReader anonymous = reader.withoutCredentials().anonymous();
    PostAccess decided = access.decide(post, anonymous);
    if (decided.hidden()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, "");
    }
    return new PostScoped<>(
        detail(author, post, decided, anonymous, null), !PostAccessPolicy.isOpen(post));
  }

  private PublicPostDetail buildDetail(
      UserSummary author, PostEntity post, PublicPostListItem item) {
    List<PostBlockEntity> entities =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId());
    Map<Long, CtaCard> ctaMap = hydrateCtas(post.getUserId(), entities);
    List<PublicPostBlockView> blocks = new ArrayList<>(entities.size());
    for (PostBlockEntity entity : entities) {
      blocks.add(buildBlockView(entity, ctaMap));
    }

    return new PublicPostDetail(
        PublicAuthorView.from(author),
        item,
        blocks,
        PostAccessPolicy.isOpen(post) ? seriesNavFor(post) : null);
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

  private Map<Long, CtaCard> hydrateCtas(Long authorId, List<PostBlockEntity> entities) {
    List<Long> ctaIds = new ArrayList<>();
    for (PostBlockEntity entity : entities) {
      if (entity.getType() != PostBlockType.CTA_REF) continue;
      Long ctaId = parseCtaId(entity.getContent());
      if (ctaId != null) ctaIds.add(ctaId);
    }
    return ctaCards.findOwnedBy(authorId, ctaIds);
  }

  private PublicPostBlockView buildBlockView(PostBlockEntity entity, Map<Long, CtaCard> ctaMap) {
    if (entity.getType() != PostBlockType.CTA_REF) {
      return PublicPostBlockView.from(entity);
    }
    Long ctaId = parseCtaId(entity.getContent());
    if (ctaId == null) {
      return PublicPostBlockView.from(entity);
    }
    CtaCard cta = ctaMap.get(ctaId);
    if (cta == null) {
      // 삭제됐거나 다른 작성자의 CTA는 빈 참조로 반환한다.
      return PublicPostBlockView.from(entity);
    }
    return PublicPostBlockView.fromWithCta(
        entity,
        new PublicPostBlockView.CtaInfo(
            cta.label(), cta.url(), cta.style(), cta.purpose(), cta.deleted()));
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

  private UserSummary resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return userSummaries
        .findActiveByUsername(normalized)
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
