package com.example.short_link.profile.application.read;

import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.PublicProfileSnapshot;
import com.example.short_link.profile.application.PublicProfileSnapshot.LinkWindow;
import com.example.short_link.profile.application.Socials;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Kept apart from ProfileQueryService so the Cacheable proxy intercepts the call and the visibility
// windows are applied to every read, cached or not.
@Component
@RequiredArgsConstructor
public class PublicProfileLoader {

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickTotalsReadRepository clickRepository;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final PublishedPostCountReader postCountReader;
  private final ShortLinkUrlBuilder urlBuilder;
  private final Clock clock;

  @Cacheable(
      value = ProfileCacheEviction.CACHE_NAME,
      key = "#username == null ? '' : #username.trim().toLowerCase(T(java.util.Locale).ROOT)")
  @Transactional(readOnly = true)
  public PublicProfileSnapshot load(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    UserEntity user =
        userRepository
            .findByUsername(normalized)
            .filter(u -> !u.isDeleted())
            .or(() -> resolveByHistory(normalized))
            .orElseThrow(
                () -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
    List<ProfileLinkRow> links = linkRepository.findPublicProfileLinks(user.getId());
    List<ProfileBlockEntity> blocks =
        profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(user.getId());
    Map<Long, Long> counts = clickCounts(links);
    List<PublicProfile.ProfileEntry> entries = entriesInProfileOrder(links, blocks, counts);
    long publishedPostCount = postCountReader.countPublishedByUserId(user.getId());
    PublicProfile profile =
        new PublicProfile(
            user.getUsername(),
            user.getBio(),
            user.getProfileTheme(),
            user.getAvatarUrl(),
            user.getBannerUrl(),
            Socials.toList(user.getSocials()),
            entries,
            publishedPostCount,
            user.isHideFollowerCount());
    return new PublicProfileSnapshot(profile, windows(links));
  }

  private Map<Long, Long> clickCounts(List<ProfileLinkRow> links) {
    Map<Long, Long> counts = new HashMap<>();
    if (!links.isEmpty()) {
      List<Long> ids = links.stream().map(row -> row.getLink().getId()).toList();
      for (LinkClickCount row : clickRepository.countsByLinkIds(ids)) {
        counts.put(row.getLinkId(), row.getCount());
      }
    }
    return counts;
  }

  private List<PublicProfile.ProfileEntry> entriesInProfileOrder(
      List<ProfileLinkRow> links, List<ProfileBlockEntity> blocks, Map<Long, Long> counts) {
    List<PublicProfile.ProfileEntry> entries = new ArrayList<>(links.size() + blocks.size());
    int linkIndex = 0;
    int blockIndex = 0;
    while (linkIndex < links.size() || blockIndex < blocks.size()) {
      ProfileLinkRow link = linkIndex < links.size() ? links.get(linkIndex) : null;
      ProfileBlockEntity block = blockIndex < blocks.size() ? blocks.get(blockIndex) : null;
      boolean takeLink =
          link != null
              && (block == null || link.getLink().getProfileOrder() <= block.getProfileOrder());
      if (takeLink) {
        entries.add(linkEntry(link, counts));
        linkIndex++;
      } else {
        entries.add(blockEntry(block));
        blockIndex++;
      }
    }
    return entries;
  }

  private PublicProfile.ProfileEntry linkEntry(ProfileLinkRow row, Map<Long, Long> counts) {
    LinkEntity link = row.getLink();
    long clicks = counts.getOrDefault(link.getId(), 0L);
    if (Boolean.TRUE.equals(row.getPasswordRequired())) {
      return PublicProfile.ProfileEntry.protectedLink(
          link.getShortCode(),
          urlBuilder.build(link.getShortCode()),
          link.getOgTitleOverride(),
          clicks,
          link.isProfileHighlighted());
    }
    return PublicProfile.ProfileEntry.link(
        link.getShortCode(),
        urlBuilder.build(link.getShortCode()),
        link.getOriginalUrl(),
        link.getEffectiveOgTitle(),
        link.getEffectiveOgImage(),
        clicks,
        link.isProfileHighlighted());
  }

  private static List<LinkWindow> windows(List<ProfileLinkRow> links) {
    return links.stream()
        .filter(row -> row.getOpensAt() != null || row.getLink().getExpiresAt() != null)
        .map(
            row ->
                new LinkWindow(
                    row.getLink().getShortCode(), row.getOpensAt(), row.getLink().getExpiresAt()))
        .toList();
  }

  private static PublicProfile.ProfileEntry blockEntry(ProfileBlockEntity block) {
    return switch (block.getType()) {
      case TEXT -> PublicProfile.ProfileEntry.text(block.getId(), block.getContent());
      case IMAGE -> PublicProfile.ProfileEntry.image(block.getId(), block.getContent());
      case EMBED -> PublicProfile.ProfileEntry.embed(block.getId(), block.getContent());
      case EMAIL_FORM -> PublicProfile.ProfileEntry.emailForm(block.getId(), block.getContent());
      case CONTACT_CARD ->
          PublicProfile.ProfileEntry.contactCard(block.getId(), block.getContent());
      case GALLERY -> PublicProfile.ProfileEntry.gallery(block.getId(), block.getContent());
      case PRODUCT_CARD ->
          PublicProfile.ProfileEntry.productCard(
              block.getId(), block.getContent(), block.isProfileHighlighted());
      case BOOKING -> PublicProfile.ProfileEntry.booking(block.getId(), block.getContent());
      case EVENT ->
          PublicProfile.ProfileEntry.event(
              block.getId(), block.getContent(), block.isProfileHighlighted());
      case PLACE -> PublicProfile.ProfileEntry.place(block.getId(), block.getContent());
      case DIVIDER -> PublicProfile.ProfileEntry.divider(block.getId());
    };
  }

  private Optional<UserEntity> resolveByHistory(String oldUsername) {
    return usernameHistoryRepository
        .findFirstByOldUsernameAndExpiresAtAfter(oldUsername, clock.instant())
        .flatMap(history -> userRepository.findById(history.getUserId()))
        .filter(u -> !u.isDeleted());
  }
}
