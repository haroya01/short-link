package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkRepository;
import org.junit.jupiter.api.Test;

class LinkServiceCollisionTest {

  @Test
  void throwsAfterMaxAttemptsAllCollide() {
    LinkRepository repository = mock(LinkRepository.class);
    ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
    when(generator.generate()).thenReturn("abc1234");
    when(repository.existsByShortCode("abc1234")).thenReturn(true);

    LinkService service = new LinkService(repository, generator);

    assertThatThrownBy(() -> service.create("https://example.com"))
        .isInstanceOf(ShortCodeGenerationException.class);

    verify(generator, times(5)).generate();
    verify(repository, never()).save(any());
  }
}
