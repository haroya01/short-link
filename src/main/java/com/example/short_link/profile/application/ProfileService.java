package com.example.short_link.profile.application;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.ClickEventRepository.LinkClickCount;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.UsernameHistoryRepository;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

  private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9_]{2,15}$");

  /** Squat-protect old usernames for this long after a rename. */
  private static final Duration USERNAME_GRACE = Duration.ofDays(30);

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final MeterRegistry meterRegistry;
  private final com.example.short_link.link.application.ShortLinkUrlBuilder urlBuilder;

  @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
  private String publicProfileBaseUrl;

  @Transactional(readOnly = true)
  public MyProfile myProfile(Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    return toMyProfile(user);
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public MyProfile updateProfile(
      Long userId, String username, String bio, String theme, String shareChannels) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (username != null) {
      String normalized = username.trim().toLowerCase();
      validateUsername(normalized);
      if (!normalized.equals(user.getUsername())) {
        if (ReservedUsernames.ALL.contains(normalized)) {
          throw new InvalidUsernameException("reserved");
        }
        userRepository
            .findByUsername(normalized)
            .filter(other -> !other.getId().equals(userId))
            .ifPresent(
                other -> {
                  throw new UsernameTakenException(normalized);
                });
        // Squat protection: another user can't reclaim a handle that's still in someone else's
        // grace window. Owner reclaiming their own previous handle is allowed (caller is same).
        usernameHistoryRepository
            .findFirstByOldUsernameAndExpiresAtAfter(normalized, Instant.now())
            .filter(history -> !history.getUserId().equals(userId))
            .ifPresent(
                history -> {
                  throw new UsernameTakenException(normalized);
                });
        // If the user already had a handle, park it in history so old SNS bio links survive 30d.
        String previous = user.getUsername();
        if (previous != null && !previous.isBlank()) {
          usernameHistoryRepository.save(
              new UsernameHistoryEntity(userId, previous, Instant.now().plus(USERNAME_GRACE)));
        }
        user.claimUsername(normalized);
      }
    }
    if (bio != null) {
      String trimmed = bio.trim();
      if (trimmed.length() > 280) {
        throw new InvalidUsernameException("bio too long");
      }
      user.updateBio(trimmed.isEmpty() ? null : trimmed);
    }
    if (theme != null) {
      user.updateProfileTheme(theme);
    }
    if (shareChannels != null) {
      String normalized = ShareChannels.normalize(shareChannels);
      user.updateShareChannels(normalized == null || normalized.isEmpty() ? null : normalized);
    }
    meterRegistry.counter("profile.updated").increment();
    return toMyProfile(user);
  }

  /**
   * Reorders the profile feed (links + text/divider blocks) to the exact sequence given. Items the
   * user doesn't own are silently skipped — defensive against the front sending stale tokens after
   * a delete. Items not in {@code itemsInOrder} keep their existing profile_order (so a featured
   * link not mentioned stays where it is, off the profile).
   */
  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void reorderProfile(Long userId, java.util.List<ReorderItem> itemsInOrder) {
    Map<String, LinkEntity> ownedLinks = new HashMap<>();
    for (var link :
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId)) {
      ownedLinks.put(link.getShortCode(), link);
    }
    Map<Long, ProfileBlockEntity> ownedBlocks = new HashMap<>();
    for (var block : profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(userId)) {
      ownedBlocks.put(block.getId(), block);
    }
    int order = 1;
    for (ReorderItem item : itemsInOrder) {
      if (item == null || item.kind() == null || item.id() == null) continue;
      switch (item.kind().toUpperCase()) {
        case "LINK" -> {
          LinkEntity link = ownedLinks.get(item.id());
          if (link != null) link.setProfileOrder(order++);
        }
        case "BLOCK" -> {
          ProfileBlockEntity block = parseBlockId(item.id()).map(ownedBlocks::get).orElse(null);
          if (block != null) block.setProfileOrder(order++);
        }
        default -> {
          /* unknown kind — skip */
        }
      }
    }
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public ProfileBlockEntity createBlock(Long userId, ProfileBlockType type, String content) {
    String validated = validateBlockContent(type, content);
    int next = nextProfileOrder(userId);
    ProfileBlockEntity block = new ProfileBlockEntity(userId, type, validated, next);
    return profileBlockRepository.save(block);
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public ProfileBlockEntity updateBlock(Long userId, Long blockId, String content) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(blockId)
            .filter(b -> b.isOwnedBy(userId))
            .orElseThrow(() -> new ProfileNotFoundException("block " + blockId));
    if (block.getType() == ProfileBlockType.DIVIDER) {
      throw new InvalidUsernameException("divider has no content");
    }
    block.updateContent(validateBlockContent(block.getType(), content));
    return block;
  }

  /**
   * Validate + normalize a block's content per type. Returns the trimmed value to persist (null for
   * types that don't carry content). Throws {@link InvalidUsernameException} on bad input — this is
   * also what the controller turns into a 400.
   */
  private static String validateBlockContent(ProfileBlockType type, String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    return switch (type) {
      case DIVIDER -> null;
      case TEXT -> {
        if (trimmed.isEmpty()) throw new InvalidUsernameException("text block content required");
        if (trimmed.length() > 120) throw new InvalidUsernameException("text block too long");
        yield trimmed;
      }
      case IMAGE -> {
        if (trimmed.isEmpty()) throw new InvalidUsernameException("image url required");
        if (trimmed.length() > 2048) throw new InvalidUsernameException("image url too long");
        // Reject anything that isn't an http(s) absolute URL — keeps `<img src>` from being abused
        // for javascript: / data: URIs in the rendered profile feed.
        try {
          java.net.URI uri = java.net.URI.create(trimmed);
          String scheme = uri.getScheme();
          if (scheme == null
              || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUsernameException("image url must be http(s)");
          }
          if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUsernameException("image url missing host");
          }
        } catch (IllegalArgumentException ex) {
          throw new InvalidUsernameException("image url malformed");
        }
        yield trimmed;
      }
    };
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void deleteBlock(Long userId, Long blockId) {
    ProfileBlockEntity block =
        profileBlockRepository
            .findById(blockId)
            .filter(b -> b.isOwnedBy(userId))
            .orElseThrow(() -> new ProfileNotFoundException("block " + blockId));
    profileBlockRepository.delete(block);
  }

  public record ReorderItem(String kind, String id) {}

  private static java.util.Optional<Long> parseBlockId(String raw) {
    try {
      return java.util.Optional.of(Long.parseLong(raw));
    } catch (NumberFormatException ex) {
      return java.util.Optional.empty();
    }
  }

  /**
   * Mark exactly one of the user's featured links as the "hero". Setting it on a new link
   * automatically clears any previous highlight — there's only ever one big card.
   */
  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void setLinkHighlight(Long userId, String shortCode, boolean highlighted) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotFoundException(shortCode);
    if (highlighted) {
      for (LinkEntity other : linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(userId)) {
        if (!other.getId().equals(link.getId())) other.setProfileHighlighted(false);
      }
      link.setProfileHighlighted(true);
    } else {
      link.setProfileHighlighted(false);
    }
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void toggleLinkOnProfile(Long userId, String shortCode, boolean show) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotFoundException(shortCode);
    }
    if (show) {
      Integer next = nextProfileOrder(userId);
      link.setProfileOrder(next);
    } else {
      link.setProfileOrder(null);
    }
  }

  @Cacheable(value = "public-profile", key = "#username")
  @Transactional(readOnly = true)
  public PublicProfile findByUsername(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase();
    UserEntity user =
        userRepository
            .findByUsername(normalized)
            .filter(u -> !u.isDeleted())
            .or(() -> resolveByHistory(normalized))
            .orElseThrow(() -> new ProfileNotFoundException(normalized));
    List<LinkEntity> links =
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(user.getId());
    List<ProfileBlockEntity> blocks =
        profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(user.getId());
    Map<Long, Long> counts = new HashMap<>();
    if (!links.isEmpty()) {
      List<Long> ids = links.stream().map(LinkEntity::getId).toList();
      for (LinkClickCount row : clickRepository.countsByLinkIds(ids)) {
        counts.put(row.getLinkId(), row.getCount());
      }
    }
    // Links and blocks share the same profile_order space (assigned together by reorderProfile).
    // Merge by order so headers / dividers slot in between links exactly where the editor put them.
    List<PublicProfile.ProfileEntry> out = new ArrayList<>(links.size() + blocks.size());
    int li = 0;
    int bi = 0;
    while (li < links.size() || bi < blocks.size()) {
      LinkEntity l = li < links.size() ? links.get(li) : null;
      ProfileBlockEntity b = bi < blocks.size() ? blocks.get(bi) : null;
      boolean takeLink = l != null && (b == null || l.getProfileOrder() <= b.getProfileOrder());
      if (takeLink) {
        out.add(
            PublicProfile.ProfileEntry.link(
                l.getShortCode(),
                urlBuilder.build(l.getShortCode()),
                l.getOriginalUrl(),
                // Prefer the user-set override so labels typed in the profile editor
                // ("📝 블로그") win over the scraped OG title.
                l.getEffectiveOgTitle(),
                l.getEffectiveOgImage(),
                counts.getOrDefault(l.getId(), 0L),
                l.isProfileHighlighted()));
        li++;
      } else {
        out.add(
            switch (b.getType()) {
              case TEXT -> PublicProfile.ProfileEntry.text(b.getId(), b.getContent());
              case IMAGE -> PublicProfile.ProfileEntry.image(b.getId(), b.getContent());
              case DIVIDER -> PublicProfile.ProfileEntry.divider(b.getId());
            });
        bi++;
      }
    }
    return new PublicProfile(
        user.getUsername(),
        user.getBio(),
        user.getProfileTheme(),
        user.getAvatarUrl(),
        user.getBannerUrl(),
        ShareChannels.toList(user.getShareChannels()),
        out);
  }

  /**
   * Used as a fallback when {@link #findByUsername} doesn't match a current user — resolves an old
   * (renamed-from) handle to the user that gave it up, as long as the grace period hasn't expired.
   * Frontend then sees {@code profile.username != requested} and can 308-redirect.
   */
  private java.util.Optional<UserEntity> resolveByHistory(String oldUsername) {
    return usernameHistoryRepository
        .findFirstByOldUsernameAndExpiresAtAfter(oldUsername, Instant.now())
        .flatMap(history -> userRepository.findById(history.getUserId()))
        .filter(u -> !u.isDeleted());
  }

  private MyProfile toMyProfile(UserEntity user) {
    String publicUrl =
        user.getUsername() == null ? null : publicProfileBaseUrl + user.getUsername();
    return new MyProfile(
        user.getUsername(),
        user.getBio(),
        user.getProfileTheme(),
        publicUrl,
        user.getAvatarUrl(),
        user.getBannerUrl(),
        ShareChannels.toList(user.getShareChannels()));
  }

  private int nextProfileOrder(Long userId) {
    int links =
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId).size();
    int blocks = (int) profileBlockRepository.countByUserId(userId);
    return links + blocks + 1;
  }

  private static void validateUsername(String username) {
    if (username.isBlank()) throw new InvalidUsernameException("blank");
    if (!USERNAME.matcher(username).matches()) {
      throw new InvalidUsernameException("invalid format");
    }
  }
}
