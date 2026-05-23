package com.example.short_link.profile.application;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.profile.contact.Booking;
import com.example.short_link.profile.contact.ContactCard;
import com.example.short_link.profile.contact.Event;
import com.example.short_link.profile.contact.Gallery;
import com.example.short_link.profile.contact.Place;
import com.example.short_link.profile.contact.ProductCardCarousel;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.UsernameHistoryEntity;
import com.example.short_link.profile.domain.UsernameHistoryRepository;
import com.example.short_link.profile.email.EmailFormConfig;
import com.example.short_link.profile.oembed.EmbedProvider;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
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
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final MeterRegistry meterRegistry;

  @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
  private String publicProfileBaseUrl;

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public MyProfile updateProfile(
      Long userId, String username, String bio, String theme, String socials) {
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
    if (socials != null) {
      String normalized = Socials.normalize(socials);
      user.updateSocials(normalized);
    }
    meterRegistry.counter("profile.updated").increment();
    return MyProfileMapper.from(user, publicProfileBaseUrl);
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
        // TEXT used to be a single-line header (120 char cap), then plain markdown (2000 cap), and
        // now a JSON payload {body, layout, accent, icon} for Toss-style highlight boxes / quote
        // rails. Legacy plain-string content is still accepted on read; on write we always emit
        // JSON via {@link TextBlockBody#normalize} so storage converges.
        yield com.example.short_link.profile.contact.TextBlockBody.normalize(raw);
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
      case EMBED -> {
        if (trimmed.isEmpty()) throw new InvalidUsernameException("embed url required");
        if (trimmed.length() > 2048) throw new InvalidUsernameException("embed url too long");
        // Provider whitelist is the SSRF guard — we'll only ever fetch oembed from these hosts.
        if (EmbedProvider.resolve(trimmed).isEmpty()) {
          throw new InvalidUsernameException("embed url: unsupported provider");
        }
        yield trimmed;
      }
      case EMAIL_FORM -> {
        if (trimmed.length() > 2048)
          throw new InvalidUsernameException("email form config too long");
        yield EmailFormConfig.normalize(trimmed);
      }
      case CONTACT_CARD -> {
        if (trimmed.length() > 2048) throw new InvalidUsernameException("contact card too long");
        yield ContactCard.normalize(trimmed);
      }
      case GALLERY -> {
        if (trimmed.length() > 2048) throw new InvalidUsernameException("gallery too long");
        yield Gallery.normalize(trimmed);
      }
      case PRODUCT_CARD -> {
        if (trimmed.length() > 16384) throw new InvalidUsernameException("product card too long");
        yield ProductCardCarousel.normalize(trimmed);
      }
      case BOOKING -> {
        if (trimmed.length() > 2048) throw new InvalidUsernameException("booking too long");
        yield Booking.normalize(trimmed);
      }
      case EVENT -> {
        if (trimmed.length() > 2048) throw new InvalidUsernameException("event too long");
        yield Event.normalize(trimmed);
      }
      case PLACE -> {
        if (trimmed.length() > 2048) throw new InvalidUsernameException("place too long");
        yield Place.normalize(trimmed);
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
