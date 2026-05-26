package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AddDestinationUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkDestinationRepository repository = mock(LinkDestinationRepository.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AddDestinationUseCase useCase =
      new AddDestinationUseCase(ownership, repository, registry);

  @Test
  void isValidUrlAcceptsHttps() {
    assertThat(AddDestinationUseCase.isValidUrl("https://example.com")).isTrue();
  }

  @Test
  void isValidUrlAcceptsHttp() {
    assertThat(AddDestinationUseCase.isValidUrl("http://example.com")).isTrue();
  }

  @Test
  void isValidUrlRejectsNull() {
    assertThat(AddDestinationUseCase.isValidUrl(null)).isFalse();
  }

  @Test
  void isValidUrlRejectsBlank() {
    assertThat(AddDestinationUseCase.isValidUrl("  ")).isFalse();
  }

  @Test
  void isValidUrlRejectsFtp() {
    assertThat(AddDestinationUseCase.isValidUrl("ftp://example.com")).isFalse();
  }

  @Test
  void clampWeightDefaultsNullToMin() {
    assertThat(AddDestinationUseCase.clampWeight(null)).isEqualTo(AddDestinationUseCase.MIN_WEIGHT);
  }

  @Test
  void clampWeightClampsBelowMin() {
    assertThat(AddDestinationUseCase.clampWeight(0)).isEqualTo(AddDestinationUseCase.MIN_WEIGHT);
  }

  @Test
  void clampWeightClampsAboveMax() {
    assertThat(AddDestinationUseCase.clampWeight(9999)).isEqualTo(AddDestinationUseCase.MAX_WEIGHT);
  }

  @Test
  void clampWeightAcceptsInRange() {
    assertThat(AddDestinationUseCase.clampWeight(50)).isEqualTo(50);
  }

  @Test
  void sanitizeLabelReturnsNullForBlank() {
    assertThat(AddDestinationUseCase.sanitizeLabel("  ")).isNull();
  }

  @Test
  void sanitizeLabelTruncatesAbove40Chars() {
    assertThat(AddDestinationUseCase.sanitizeLabel("a".repeat(50))).hasSize(40);
  }

  @Test
  void executeThrowsWhenUrlInvalid() {
    LinkEntity link = withId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(ownership.ownedLink(42L, new ShortCode("abc1234"))).thenReturn(link);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    42L, new ShortCode("abc1234"), "ftp://x.com", null, null, null, null, null))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void executeThrowsWhenDestinationCountAtLimit() {
    LinkEntity link = withId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(ownership.ownedLink(42L, new ShortCode("abc1234"))).thenReturn(link);
    when(repository.countByLinkId(new LinkId(1L)))
        .thenReturn((long) AddDestinationUseCase.MAX_PER_LINK);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    42L,
                    new ShortCode("abc1234"),
                    "https://dest.com",
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void executeSavesAndReturnsSummary() {
    LinkEntity link = withId(new LinkEntity("https://x.com", "abc1234", 42L, null), 1L);
    when(ownership.ownedLink(42L, new ShortCode("abc1234"))).thenReturn(link);
    when(repository.countByLinkId(new LinkId(1L))).thenReturn(0L);
    LinkDestinationEntity saved =
        withId(
            new LinkDestinationEntity(
                new LinkId(1L), "https://dest.com", 50, null, null, null, null),
            99L);
    when(repository.save(any(LinkDestinationEntity.class))).thenReturn(saved);

    DestinationSummary result =
        useCase.execute(
            42L, new ShortCode("abc1234"), "https://dest.com", 50, null, null, null, null);

    assertThat(result.id()).isEqualTo(99L);
  }

  private static <T> T withId(T entity, Long id) {
    try {
      java.lang.reflect.Field f = entity.getClass().getDeclaredField("id");
      f.setAccessible(true);
      f.set(entity, id);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    return entity;
  }
}
