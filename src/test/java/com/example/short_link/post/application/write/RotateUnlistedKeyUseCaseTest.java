package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.application.access.PostPasswords;
import com.example.short_link.post.application.access.PostUnlistedUrls;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RotateUnlistedKeyUseCaseTest {

  private final PostOwnership ownership = mock(PostOwnership.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final RotateUnlistedKeyUseCase useCase =
      new RotateUnlistedKeyUseCase(
          ownership,
          new PostAccessSettings(new PostPasswords()),
          posts,
          new PostUnlistedUrls("https://blog.kurl.me", users));

  @Test
  void aNewKeyReplacesTheOldOneInTheShareableLink() {
    UserEntity john = new UserEntity("j@x.com", "google", "g-1");
    john.claimUsername("john");
    when(users.findById(7L)).thenReturn(Optional.of(john));
    PostEntity post = new PostEntity(7L, "p", "Title", "ko");
    post.changeAudience(PostAudience.UNLISTED, "old-key");
    when(ownership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    String url = useCase.rotate(7L, 42L);

    assertThat(post.getUnlistedKey()).isNotEqualTo("old-key").hasSize(32);
    assertThat(url).isEqualTo("https://blog.kurl.me/@john/p?k=" + post.getUnlistedKey());
    verify(posts).save(post);
  }

  @Test
  void aPostThatIsNotUnlistedKeepsANewKeyForLaterButHasNoLink() {
    PostEntity post = new PostEntity(7L, "p", "Title", "ko");
    when(ownership.requireOwnedForUpdate(7L, 42L)).thenReturn(post);

    assertThat(useCase.rotate(7L, 42L)).isNull();
    assertThat(post.getUnlistedKey()).hasSize(32);
  }
}
