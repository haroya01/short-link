package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.notification.LinkNotice;
import com.example.short_link.common.notification.LinkNoticeBody;
import com.example.short_link.common.notification.LinkNoticeKind;
import com.example.short_link.link.health.application.DestinationBrokenEvent;
import com.example.short_link.link.health.domain.DestinationFailure;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DestinationBrokenNoticeListenerTest {

  private final List<LinkNotice> sent = new ArrayList<>();
  private final DestinationBrokenNoticeListener listener =
      new DestinationBrokenNoticeListener(sent::add);

  @Test
  void aMissingPageNamesItsStatusForTheOwnersLanguage() {
    listener.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "春セール", DestinationFailure.NOT_FOUND, 404));

    assertThat(sent)
        .containsExactly(
            new LinkNotice(
                42L,
                LinkNoticeKind.DESTINATION_BROKEN,
                "abc1234",
                "春セール",
                new LinkNoticeBody.Localized(
                    "notification.link.destinationBroken.missing", "404")));
  }

  @Test
  void aGonePageWithoutAStatusLeavesTheStatusEmpty() {
    listener.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "/abc1234", DestinationFailure.GONE, null));

    assertThat(sent)
        .extracting(LinkNotice::body)
        .containsExactly(
            new LinkNoticeBody.Localized("notification.link.destinationBroken.missing", ""));
  }

  @Test
  void aVanishedDomainGetsItsOwnWords() {
    listener.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "/abc1234", DestinationFailure.NO_HOST, null));

    assertThat(sent)
        .extracting(LinkNotice::body)
        .containsExactly(
            new LinkNoticeBody.Localized("notification.link.destinationBroken.noHost", ""));
  }

  @Test
  void aFailedSendIsSwallowed() {
    DestinationBrokenNoticeListener failing =
        new DestinationBrokenNoticeListener(
            notice -> {
              throw new IllegalStateException("inbox down");
            });

    failing.onDestinationBroken(
        new DestinationBrokenEvent(42L, "abc1234", "/abc1234", DestinationFailure.NO_HOST, null));
  }
}
