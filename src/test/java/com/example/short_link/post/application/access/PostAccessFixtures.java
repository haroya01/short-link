package com.example.short_link.post.application.access;

import static org.mockito.Mockito.mock;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.post.application.read.PostRooms;
import com.example.short_link.post.application.write.CommentNotifications;
import com.example.short_link.post.application.write.PostAccessSettings;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.user.application.read.FollowingReader;
import com.example.short_link.user.application.read.UserSummaryReader;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;

public final class PostAccessFixtures {

  private PostAccessFixtures() {}

  public static PostAccessPolicy policy() {
    return policy(mock(FollowRepository.class), null);
  }

  public static PostAccessPolicy policy(FollowRepository follows, String readerCountry) {
    return policy(follows, readerCountry, mock(PostRoomReader.class));
  }

  public static PostAccessPolicy policy(PostRoomReader rooms) {
    return policy(mock(FollowRepository.class), null, rooms);
  }

  private static PostAccessPolicy policy(
      FollowRepository follows, String readerCountry, PostRoomReader rooms) {
    GeoIpResolver geo =
        mock(GeoIpResolver.class, invocation -> new GeoLocation(readerCountry, null, null));
    return new PostAccessPolicy(
        new FollowingReader(follows),
        new PostGrants("", Clock.systemUTC()),
        new ReaderCountryResolver(geo),
        rooms);
  }

  public static PostRoomReader roomReader() {
    return mock(PostRoomReader.class);
  }

  public static PostRooms rooms() {
    return new PostRooms(roomReader());
  }

  public static PostAccessSettings accessSettings() {
    return new PostAccessSettings(new PostPasswords(), roomReader());
  }

  public static CommentNotifications notifications() {
    return new CommentNotifications(
        new UserSummaryReader(mock(UserRepository.class)),
        mock(PostRepository.class),
        mock(ApplicationEventPublisher.class),
        mock(BlogNotificationMuteReader.class),
        policy());
  }

  public static PostUnlistedUrls unlistedUrls() {
    return new PostUnlistedUrls(
        "https://blog.kurl.me", new UserSummaryReader(mock(UserRepository.class)));
  }
}
