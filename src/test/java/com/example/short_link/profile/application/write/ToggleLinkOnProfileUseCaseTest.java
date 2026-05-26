package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToggleLinkOnProfileUseCaseTest {

  @Mock private LinkRepository linkRepository;
  @Mock private ProfileOrdering profileOrdering;

  private ToggleLinkOnProfileUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new ToggleLinkOnProfileUseCase(
            linkRepository,
            mock(LinkProfileBindingRepository.class),
            profileOrdering,
            mock(ProfileCacheEviction.class));
  }

  @Test
  void showAssignsNextOrder() {
    LinkEntity link = new LinkEntity("https://t", "abc", 7L, null);
    when(linkRepository.findByShortCode(new ShortCode("abc"))).thenReturn(Optional.of(link));
    when(profileOrdering.nextOrder(7L)).thenReturn(2);
    useCase.execute(new ToggleLinkOnProfileCommand(7L, new ShortCode("abc"), true));
    assertThat(link.getProfileOrder()).isEqualTo(2);
  }

  @Test
  void hideClearsOrder() {
    LinkEntity link = new LinkEntity("https://t", "abc", 7L, null);
    link.setProfileOrder(5);
    when(linkRepository.findByShortCode(new ShortCode("abc"))).thenReturn(Optional.of(link));
    useCase.execute(new ToggleLinkOnProfileCommand(7L, new ShortCode("abc"), false));
    assertThat(link.getProfileOrder()).isNull();
  }

  @Test
  void missingThrows() {
    when(linkRepository.findByShortCode(new ShortCode("missing"))).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                useCase.execute(new ToggleLinkOnProfileCommand(7L, new ShortCode("missing"), true)))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void notOwnerThrows() {
    LinkEntity link = new LinkEntity("https://t", "abc", 99L, null);
    when(linkRepository.findByShortCode(new ShortCode("abc"))).thenReturn(Optional.of(link));
    assertThatThrownBy(
            () -> useCase.execute(new ToggleLinkOnProfileCommand(7L, new ShortCode("abc"), true)))
        .isInstanceOf(LinkException.class);
  }
}
