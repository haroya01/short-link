package com.example.short_link.profile.application.read;

import com.example.short_link.common.post.PublishedPostCountReader;
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
  private final PublishedPostCountReader postCountReader;
  private final ShortLinkUrlBuilder urlBuilder;
  private final PublicHandleReader publicHandles;
  private final String publicProfileBaseUrl;

  public ProfileQueryService(
      UserRepository userRepository,
      LinkRepository linkRepository,
      ClickTotalsReadRepository clickRepository,
      UsernameHistoryRepository usernameHistoryRepository,
      ProfileBlockRepository profileBlockRepository,
      PublishedPostCountReader postCountReader,
      ShortLinkUrlBuilder urlBuilder,
      PublicHandleReader publicHandles,
      @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
          String publicProfileBaseUrl) {
    this.userRepository = userRepository;
    this.linkRepository = linkRepository;
    this.clickRepository = clickRepository;
    this.usernameHistoryRepository = usernameHistoryRepository;
    this.profileBlockRepository = profileBlockRepository;
    this.postCountReader = postCountReader;
    this.urlBuilder = urlBuilder;
    this.publicHandles = publicHandles;
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
    Map<Long, Long> counts = clickCounts(links);
    List<PublicProfile.ProfileEntry> out = entriesInProfileOrder(links, blocks, counts);
    long publishedPostCount = postCountReader.countPublishedByUserId(user.getId());
    return new PublicProfile(
        user.getUsername(),
        user.getBio(),
        user.getProfileTheme(),
        user.getAvatarUrl(),
        user.getBannerUrl(),
        Socials.toList(user.getSocials()),
        out,
        publishedPostCount,
        user.isHideFollowerCount());
  }

  private Map<Long, Long> clickCounts(List<LinkEntity> links) {
    Map<Long, Long> counts = new HashMap<>();
    if (!links.isEmpty()) {
      List<Long> ids = links.stream().map(LinkEntity::getId).toList();
      for (LinkClickCount row : clickRepository.countsByLinkIds(ids)) {
        counts.put(row.getLinkId(), row.getCount());
      }
    }
    return counts;
  }

  private List<PublicProfile.ProfileEntry> entriesInProfileOrder(
      List<LinkEntity> links, List<ProfileBlockEntity> blocks, Map<Long, Long> counts) {
    List<PublicProfile.ProfileEntry> entries = new ArrayList<>(links.size() + blocks.size());
    int linkIndex = 0;
    int blockIndex = 0;
    while (linkIndex < links.size() || blockIndex < blocks.size()) {
      LinkEntity link = linkIndex < links.size() ? links.get(linkIndex) : null;
      ProfileBlockEntity block = blockIndex < blocks.size() ? blocks.get(blockIndex) : null;
      boolean takeLink =
          link != null && (block == null || link.getProfileOrder() <= block.getProfileOrder());
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

  private PublicProfile.ProfileEntry linkEntry(LinkEntity link, Map<Long, Long> counts) {
    return PublicProfile.ProfileEntry.link(
        link.getShortCode(),
        urlBuilder.build(link.getShortCode()),
        link.getOriginalUrl(),
        link.getEffectiveOgTitle(),
        link.getEffectiveOgImage(),
        counts.getOrDefault(link.getId(), 0L),
        link.isProfileHighlighted());
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
          PublicProfile.ProfileEntry.productCard(block.getId(), block.getContent());
      case BOOKING -> PublicProfile.ProfileEntry.booking(block.getId(), block.getContent());
      case EVENT -> PublicProfile.ProfileEntry.event(block.getId(), block.getContent());
      case PLACE -> PublicProfile.ProfileEntry.place(block.getId(), block.getContent());
      case DIVIDER -> PublicProfile.ProfileEntry.divider(block.getId());
    };
  }

  public PublicHandlesPage publicHandlesPage(int page, int size) {
    long total = userRepository.countByUsernameIsNotNullAndDeletedAtIsNull();
    List<String> handles = publicHandles.findPage(page, size);
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
