package com.example.short_link.profile.application.read;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.MyProfileMapper;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.PublicProfileSnapshot;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileQueryService {

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final PublicProfileLoader publicProfiles;
  private final PublicHandleReader publicHandles;
  private final Clock clock;
  private final String publicProfileBaseUrl;

  public ProfileQueryService(
      UserRepository userRepository,
      LinkRepository linkRepository,
      PublicProfileLoader publicProfiles,
      PublicHandleReader publicHandles,
      Clock clock,
      @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
          String publicProfileBaseUrl) {
    this.userRepository = userRepository;
    this.linkRepository = linkRepository;
    this.publicProfiles = publicProfiles;
    this.publicHandles = publicHandles;
    this.clock = clock;
    this.publicProfileBaseUrl = publicProfileBaseUrl;
  }

  public MyProfile myProfile(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    return MyProfileMapper.from(user, publicProfileBaseUrl);
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public PublicProfile findByUsername(String username) {
    PublicProfileSnapshot snapshot = publicProfiles.load(username);
    // View counts move after the profile is cached, so a limited link's count is read per request.
    Set<ShortCode> limited = snapshot.viewLimitedLinks();
    Set<ShortCode> spent =
        limited.isEmpty()
            ? Set.of()
            : Set.copyOf(linkRepository.findShortCodesWithViewLimitReached(limited));
    return snapshot.visibleAt(clock.instant(), spent);
  }

  public PublicHandlesPage publicHandlesPage(int page, int size) {
    long total = userRepository.countByUsernameIsNotNullAndDeletedAtIsNull();
    List<String> handles = publicHandles.findPage(page, size);
    return new PublicHandlesPage(handles, total);
  }

  public record PublicHandlesPage(List<String> handles, long total) {}
}
