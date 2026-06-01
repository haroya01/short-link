package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.ShortLinkDetector;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.application.write.CreateLinkCommand;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.ShortCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtaLinkTrackerTest {

  @Mock private CreateLinkUseCase createLink;
  @Mock private ShortLinkDetector detector;

  private CtaLinkTracker tracker() {
    return new CtaLinkTracker(createLink, detector);
  }

  @Test
  void wrapsExternalUrlIntoShortLink() {
    when(detector.extractCode("https://example.com/join")).thenReturn(null);
    when(createLink.execute(any(CreateLinkCommand.class)))
        .thenReturn(new LinkCreated(ShortCode.of("abc123")));

    String code = tracker().trackingCodeFor(7L, "https://example.com/join");

    assertThat(code).isEqualTo("abc123");
  }

  @Test
  void reusesExistingKurlLinkWithoutWrapping() {
    when(detector.extractCode("https://kurl.me/xy12")).thenReturn("xy12");

    String code = tracker().trackingCodeFor(7L, "https://kurl.me/xy12");

    assertThat(code).isEqualTo("xy12");
    verify(createLink, never()).execute(any());
  }

  @Test
  void skipsTrackingWhenLinkCreationFails() {
    when(detector.extractCode(any())).thenReturn(null);
    when(createLink.execute(any(CreateLinkCommand.class)))
        .thenThrow(new RuntimeException("link quota exceeded"));

    String code = tracker().trackingCodeFor(7L, "https://example.com/x");

    assertThat(code).isNull();
  }

  @Test
  void nullUrlIsNotTracked() {
    assertThat(tracker().trackingCodeFor(7L, null)).isNull();
    verify(createLink, never()).execute(any());
  }
}
