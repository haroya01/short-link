package com.example.short_link.profile.application.read;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.MyProfileMapper;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.Socials;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.domain.repository.UsernameHistoryRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileQueryService {

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickTotalsReadRepository clickRepository;
  private final UsernameHistoryRepository usernameHistoryRepository;
  private final ProfileBlockRepository profileBlockRepository;
  private final ShortLinkUrlBuilder urlBuilder;
  private final String publicProfileBaseUrl;

  public ProfileQueryService(
      UserRepository userRepository,
      LinkRepository linkRepository,
      ClickTotalsReadRepository clickRepository,
      UsernameHistoryRepository usernameHistoryRepository,
      ProfileBlockRepository profileBlockRepository,
      ShortLinkUrlBuilder urlBuilder,
      @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
          String publicProfileBaseUrl) {
    this.userRepository = userRepository;
    this.linkRepository = linkRepository;
    this.clickRepository = clickRepository;
    this.usernameHistoryRepository = usernameHistoryRepository;
    this.profileBlockRepository = profileBlockRepository;
    this.urlBuilder = urlBuilder;
    this.publicProfileBaseUrl = publicProfileBaseUrl;
  }

  public MyProfile myProfile(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    return MyProfileMapper.from(user, publicProfileBaseUrl);
  }

  @Cacheable(
      value = "public-profile",
      key = "#username == null ? '' : #username.trim().toLowerCase()")
  public PublicProfile findByUsername(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase();
    UserEntity user =
        userRepository
            .findByUsername(normalized)
            .filter(u -> !u.isDeleted())
            .or(() -> resolveByHistory(normalized))
            .orElseThrow(
                () -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
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
                l.getShortCode().value(),
                urlBuilder.build(l.getShortCode().value()),
                l.getOriginalUrl(),
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
              case EMBED -> PublicProfile.ProfileEntry.embed(b.getId(), b.getContent());
              case EMAIL_FORM -> PublicProfile.ProfileEntry.emailForm(b.getId(), b.getContent());
              case CONTACT_CARD ->
                  PublicProfile.ProfileEntry.contactCard(b.getId(), b.getContent());
              case GALLERY -> PublicProfile.ProfileEntry.gallery(b.getId(), b.getContent());
              case PRODUCT_CARD ->
                  PublicProfile.ProfileEntry.productCard(b.getId(), b.getContent());
              case BOOKING -> PublicProfile.ProfileEntry.booking(b.getId(), b.getContent());
              case EVENT -> PublicProfile.ProfileEntry.event(b.getId(), b.getContent());
              case PLACE -> PublicProfile.ProfileEntry.place(b.getId(), b.getContent());
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
        Socials.toList(user.getSocials()),
        out);
  }

  /**
   * sitemap 용 paginated 공개 handle 목록 (login 무관 anonymous 노출 안전). page 는 0-based, size 는 caller 가
   * 1∼1000 범위 보장.
   */
  public PublicHandlesPage publicHandlesPage(int page, int size) {
    long total = userRepository.countByUsernameIsNotNullAndDeletedAtIsNull();
    List<String> handles =
        userRepository
            .findAllByUsernameIsNotNullAndDeletedAtIsNullOrderByCreatedAtAsc(
                org.springframework.data.domain.PageRequest.of(page, size))
            .stream()
            .map(UserEntity::getUsername)
            .toList();
    return new PublicHandlesPage(handles, total);
  }

  public record PublicHandlesPage(List<String> handles, long total) {}

  private Optional<UserEntity> resolveByHistory(String oldUsername) {
    return usernameHistoryRepository
        .findFirstByOldUsernameAndExpiresAtAfter(oldUsername, Instant.now())
        .flatMap(history -> userRepository.findById(history.getUserId()))
        .filter(u -> !u.isDeleted());
  }
}
