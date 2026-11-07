package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.write.CheckedDestination;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.ShortCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtaLinkTrackerTest {

  @Mock private CreateLinkUseCase createLink;
  @Mock private ShortLinkDetector detector;

  private final CheckedDestination checked = mock(CheckedDestination.class);

  private String track(String url) {
    return new CtaLinkTracker(createLink, detector, allowed -> true).trackingCodeFor(7L, url);
  }

  @Test
  void aDestinationTheOutboundPolicyRefusesIsRefusedBeforeAnyLinkIsMade() {
    CtaLinkTracker tracker =
        new CtaLinkTracker(createLink, detector, url -> !url.contains("phish.test"));

    assertThatThrownBy(() -> tracker.trackingCodeFor(7L, "https://phish.test/login"))
        .isInstanceOfSatisfying(
            CtaException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CtaErrorCode.MALICIOUS_URL));
    verifyNoInteractions(createLink);
  }

  @Test
  void wrapsExternalUrlIntoShortLink() {
    when(detector.extractCode("https://example.com/join")).thenReturn(null);
    when(createLink.check(any(CreateLinkCommand.class))).thenReturn(checked);
    when(createLink.tryExecute(any(CreateLinkCommand.class), eq(checked)))
        .thenReturn(Optional.of(new LinkCreated(ShortCode.of("abc123"))));

    assertThat(track("https://example.com/join")).isEqualTo("abc123");
  }

  @Test
  void reusesExistingKurlLinkWithoutWrapping() {
    when(detector.extractCode("https://kurl.me/xy12")).thenReturn("xy12");

    assertThat(track("https://kurl.me/xy12")).isEqualTo("xy12");
    verify(createLink, never()).check(any());
    verify(createLink, never()).tryExecute(any(), any());
  }

  @Test
  void skipsTrackingWhenAnotherLinkCheckFails() {
    when(detector.extractCode(any())).thenReturn(null);
    when(createLink.check(any(CreateLinkCommand.class)))
        .thenThrow(new RuntimeException("self reference"));

    assertThat(track("https://example.com/x")).isNull();
    verify(createLink, never()).tryExecute(any(), any());
  }

  @Test
  void skipsTrackingWhenLinkCreationFails() {
    when(detector.extractCode(any())).thenReturn(null);
    when(createLink.check(any(CreateLinkCommand.class))).thenReturn(checked);
    when(createLink.tryExecute(any(CreateLinkCommand.class), eq(checked)))
        .thenReturn(Optional.empty());

    assertThat(track("https://example.com/x")).isNull();
  }

  @Test
  void nullUrlIsNotTracked() {
    assertThat(track(null)).isNull();
    verify(createLink, never()).check(any());
  }
}
