package com.example.short_link.profile.application.read;

import com.example.short_link.link.application.read.ProfileLinks;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.MyProfileMapper;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.PublicProfileSnapshot;
import com.example.short_link.user.application.read.UserProfile;
import com.example.short_link.user.application.read.UserProfileReader;
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

  private final UserProfileReader users;
  private final ProfileLinks profileLinks;
  private final PublicProfileLoader publicProfiles;
  private final Clock clock;
  private final String publicProfileBaseUrl;

  public ProfileQueryService(
      UserProfileReader users,
      ProfileLinks profileLinks,
      PublicProfileLoader publicProfiles,
      Clock clock,
      @Value("${short-link.public-profile-base-url:http://localhost:3001/u/}")
          String publicProfileBaseUrl) {
    this.users = users;
    this.profileLinks = profileLinks;
    this.publicProfiles = publicProfiles;
    this.clock = clock;
    this.publicProfileBaseUrl = publicProfileBaseUrl;
  }

  public MyProfile myProfile(Long userId) {
    UserProfile user =
        users.find(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    return MyProfileMapper.from(user, publicProfileBaseUrl);
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public PublicProfile findByUsername(String username) {
    PublicProfileSnapshot snapshot = publicProfiles.load(username);
    // View counts move after the profile is cached, so a limited link's count is read per request.
    Set<ShortCode> limited = snapshot.viewLimitedLinks();
    Set<ShortCode> spent =
        limited.isEmpty() ? Set.of() : Set.copyOf(profileLinks.viewLimitReached(limited));
    return snapshot.visibleAt(clock.instant(), spent);
  }

  public PublicHandlesPage publicHandlesPage(int page, int size) {
    long total = users.countPublicHandles();
    List<String> handles = users.publicHandles(page, size);
    return new PublicHandlesPage(handles, total);
  }

  public record PublicHandlesPage(List<String> handles, long total) {}
}
