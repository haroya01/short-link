package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetLinkHighlightUseCaseTest {

  @Mock private LinkRepository linkRepository;

  private SetLinkHighlightUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetLinkHighlightUseCase(linkRepository);
  }

  @Test
  void throwsWhenLinkMissing() {
    when(linkRepository.findByShortCode("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(new SetLinkHighlightCommand(7L, "missing", true)))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void throwsWhenNotOwner() {
    LinkEntity link = new LinkEntity("https://x", "abc", 99L, null);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(link));
    assertThatThrownBy(() -> useCase.execute(new SetLinkHighlightCommand(7L, "abc", true)))
        .isInstanceOf(LinkNotFoundException.class);
  }

  @Test
  void enablesAndClearsOtherHighlights() {
    LinkEntity target = new LinkEntity("https://t", "abc", 7L, null);
    writeField(target, "id", 1L);
    LinkEntity existing = new LinkEntity("https://e", "xyz", 7L, null);
    writeField(existing, "id", 2L);
    existing.setProfileHighlighted(true);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(target));
    when(linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(7L))
        .thenReturn(List.of(existing));
    useCase.execute(new SetLinkHighlightCommand(7L, "abc", true));
    assertThat(target.isProfileHighlighted()).isTrue();
    assertThat(existing.isProfileHighlighted()).isFalse();
  }

  @Test
  void disable() {
    LinkEntity target = new LinkEntity("https://t", "abc", 7L, null);
    target.setProfileHighlighted(true);
    when(linkRepository.findByShortCode("abc")).thenReturn(Optional.of(target));
    useCase.execute(new SetLinkHighlightCommand(7L, "abc", false));
    assertThat(target.isProfileHighlighted()).isFalse();
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
