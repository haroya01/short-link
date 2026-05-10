package com.example.short_link.profile.application;

import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.ClickEventRepository.LinkClickCount;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
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

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final MeterRegistry meterRegistry;

  @Value("${short-link.public-profile-base-url:https://kurl.me/u/}")
  private String publicProfileBaseUrl;

  @Transactional(readOnly = true)
  public MyProfile myProfile(Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    return toMyProfile(user);
  }

  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public MyProfile updateProfile(Long userId, String username, String bio, String theme) {
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
    meterRegistry.counter("profile.updated").increment();
    return toMyProfile(user);
  }

  /**
   * Reorders the user's featured links to the exact sequence given. Codes not in the user's
   * collection are silently skipped — defensive against the front sending stale data after a
   * delete. Codes not present here keep their existing profileOrder (off the profile).
   */
  @Transactional
  @CacheEvict(value = "public-profile", allEntries = true)
  public void reorderFeatured(Long userId, java.util.List<String> shortCodesInOrder) {
    java.util.Map<String, com.example.short_link.link.domain.LinkEntity> owned =
        new java.util.HashMap<>();
    for (var link :
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId)) {
      owned.put(link.getShortCode(), link);
    }
    int order = 1;
    for (String code : shortCodesInOrder) {
      var link = owned.get(code);
      if (link == null) continue;
      link.setProfileOrder(order++);
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
            .orElseThrow(() -> new ProfileNotFoundException(normalized));
    List<LinkEntity> links =
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(user.getId());
    Map<Long, Long> counts = new HashMap<>();
    if (!links.isEmpty()) {
      List<Long> ids = links.stream().map(LinkEntity::getId).toList();
      for (LinkClickCount row : clickRepository.countsByLinkIds(ids)) {
        counts.put(row.getLinkId(), row.getCount());
      }
    }
    List<PublicProfile.ProfileLink> out =
        links.stream()
            .map(
                l ->
                    new PublicProfile.ProfileLink(
                        l.getShortCode(),
                        publicLinkUrl(l.getShortCode()),
                        l.getOriginalUrl(),
                        // Prefer the user-set override so labels typed in the profile editor
                        // ("📝 블로그") win over the scraped OG title.
                        l.getEffectiveOgTitle(),
                        counts.getOrDefault(l.getId(), 0L)))
            .toList();
    return new PublicProfile(user.getUsername(), user.getBio(), user.getProfileTheme(), out);
  }

  private MyProfile toMyProfile(UserEntity user) {
    String publicUrl =
        user.getUsername() == null ? null : publicProfileBaseUrl + user.getUsername();
    return new MyProfile(user.getUsername(), user.getBio(), user.getProfileTheme(), publicUrl);
  }

  private Integer nextProfileOrder(Long userId) {
    return linkRepository
            .findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId)
            .size()
        + 1;
  }

  private static void validateUsername(String username) {
    if (username.isBlank()) throw new InvalidUsernameException("blank");
    if (!USERNAME.matcher(username).matches()) {
      throw new InvalidUsernameException("invalid format");
    }
  }

  private String publicLinkUrl(String shortCode) {
    // The actual short URL host is the redirect base, not the profile base. We surface it as part
    // of the profile so the front can render copy/QR buttons without a second round-trip — the
    // URL builder isn't injectable here without a circular dep, so we strip /u/ and reuse host.
    String base = publicProfileBaseUrl.replaceAll("/u/?$", "/");
    if (!base.endsWith("/")) base = base + "/";
    return base + shortCode;
  }
}
