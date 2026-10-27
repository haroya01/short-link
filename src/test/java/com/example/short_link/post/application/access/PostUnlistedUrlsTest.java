package com.example.short_link.post.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.user.application.read.UserSummaryReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PostUnlistedUrlsTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PostUnlistedUrls urls =
      new PostUnlistedUrls("https://blog.kurl.me/", new UserSummaryReader(users));

  @Test
  void anUnlistedPostLinksToItsPageWithItsKey() {
    PostEntity post = post("my post", PostAudience.UNLISTED);

    assertThat(urls.of(post, "john"))
        .isEqualTo("https://blog.kurl.me/@john/my%20post?k=" + post.getUnlistedKey());
  }

  @Test
  void anyOtherAudienceHasNoUnlistedLinkAndAsksNothing() {
    PostEntity post = post("p", PostAudience.FOLLOWERS);

    assertThat(urls.of(post)).isNull();
    assertThat(urls.of(post, "john")).isNull();
    verifyNoInteractions(users);
  }

  @Test
  void anAuthorsListLooksTheirNameUpOnce() {
    UserEntity john = new UserEntity("j@x.com", "google", "g-1");
    john.claimUsername("john");
    when(users.findById(7L)).thenReturn(Optional.of(john));
    Function<PostEntity, String> forAuthors = urls.forAuthors();

    assertThat(forAuthors.apply(post("a", PostAudience.UNLISTED)))
        .startsWith("https://blog.kurl.me/@john/a?k=");
    assertThat(forAuthors.apply(post("b", PostAudience.UNLISTED)))
        .startsWith("https://blog.kurl.me/@john/b?k=");
    verify(users, times(1)).findById(7L);
  }

  private static PostEntity post(String slug, PostAudience audience) {
    PostEntity post = new PostEntity(7L, slug, "Title", "ko");
    post.changeAudience(audience, null, "key-" + slug);
    return post;
  }
}
