package com.example.short_link.profile.application.read;

import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.link.application.read.ProfileLinkCard;
import com.example.short_link.link.application.read.ProfileLinks;
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
import com.example.short_link.user.application.read.UserProfile;
import com.example.short_link.user.application.read.UserProfileReader;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  private final UserProfileReader users;
  private final ProfileLinks profileLinks;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final PublishedPostCountReader postCountReader;
  private final Clock clock;

  @Cacheable(
      value = ProfileCacheEviction.CACHE_NAME,
      key = "#username == null ? '' : #username.trim().toLowerCase(T(java.util.Locale).ROOT)")
  @Transactional(readOnly = true)
  public PublicProfileSnapshot load(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    UserProfile user =
        users
            .findActiveByUsername(normalized)
            .or(() -> resolveByHistory(normalized))
            .orElseThrow(
                () -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
    List<ProfileLinkCard> links = profileLinks.publicCards(user.id());
    List<ProfileBlockEntity> blocks =
        profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(user.id());
    List<PublicProfile.ProfileEntry> entries = entriesInProfileOrder(links, blocks);
    long publishedPostCount = postCountReader.countPublishedByUserId(user.id());
    PublicProfile profile =
        new PublicProfile(
            user.username(),
            user.bio(),
            user.profileTheme(),
            user.avatarUrl(),
            user.bannerUrl(),
            Socials.toList(user.socials()),
            entries,
            publishedPostCount,
            user.hideFollowerCount());
    return new PublicProfileSnapshot(profile, windows(links));
  }

  private List<PublicProfile.ProfileEntry> entriesInProfileOrder(
      List<ProfileLinkCard> links, List<ProfileBlockEntity> blocks) {
    List<PublicProfile.ProfileEntry> entries = new ArrayList<>(links.size() + blocks.size());
    int linkIndex = 0;
    int blockIndex = 0;
    while (linkIndex < links.size() || blockIndex < blocks.size()) {
      ProfileLinkCard link = linkIndex < links.size() ? links.get(linkIndex) : null;
      ProfileBlockEntity block = blockIndex < blocks.size() ? blocks.get(blockIndex) : null;
      boolean takeLink =
          link != null && (block == null || link.profileOrder() <= block.getProfileOrder());
      if (takeLink) {
        entries.add(linkEntry(link));
        linkIndex++;
      } else {
        entries.add(blockEntry(block));
        blockIndex++;
      }
    }
    return entries;
  }

  private static PublicProfile.ProfileEntry linkEntry(ProfileLinkCard link) {
    if (link.passwordRequired()) {
      return PublicProfile.ProfileEntry.protectedLink(
          link.shortCode(),
          link.url(),
          link.preview().titleOverride(),
          link.clicks(),
          link.highlighted());
    }
    return PublicProfile.ProfileEntry.link(
        link.shortCode(),
        link.url(),
        link.originalUrl(),
        link.preview().title(),
        link.preview().image(),
        link.clicks(),
        link.highlighted());
  }

  private static List<LinkWindow> windows(List<ProfileLinkCard> links) {
    return links.stream()
        .filter(link -> link.window().limits())
        .map(
            link ->
                new LinkWindow(
                    link.shortCode(),
                    link.window().opensAt(),
                    link.window().expiresAt(),
                    link.window().viewLimited()))
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

  private Optional<UserProfile> resolveByHistory(String oldUsername) {
    return usernameHistoryRepository
        .findFirstByOldUsernameAndExpiresAtAfter(oldUsername, clock.instant())
        .flatMap(history -> users.findActive(history.getUserId()));
  }
}
