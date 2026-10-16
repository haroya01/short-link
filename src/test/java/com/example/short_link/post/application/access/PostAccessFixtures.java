package com.example.short_link.post.application.access;

import static org.mockito.Mockito.mock;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;

public final class PostAccessFixtures {

  private PostAccessFixtures() {}

  public static PostAccessPolicy policy() {
    return policy(mock(FollowRepository.class), null);
  }

  public static PostAccessPolicy policy(FollowRepository follows, String readerCountry) {
    GeoIpResolver geo =
        mock(GeoIpResolver.class, invocation -> new GeoLocation(readerCountry, null, null));
    return new PostAccessPolicy(
        follows, new PostGrants("", Clock.systemUTC()), new ReaderCountryResolver(geo));
  }

  public static PostUnlistedUrls unlistedUrls() {
    return new PostUnlistedUrls("https://blog.kurl.me", mock(UserRepository.class));
  }
}
