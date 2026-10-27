package com.example.short_link.admin.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.helper.WarningCopy;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockedDomainWarningFanoutTest {

  private final LinkLookupQueryService links = mock(LinkLookupQueryService.class);
  private final UserSummaryReader users = mock(UserSummaryReader.class);
  private final LinkNotificationDispatcher dispatcher = mock(LinkNotificationDispatcher.class);
  private final BlockedDomainWarningFanout fanout =
      new BlockedDomainWarningFanout(links, users, dispatcher);

  private static LinkRef link(Long owner, String code, String url) {
    return new LinkRef(new LinkId(1L), owner, new ShortCode(code), url);
  }

  @Test
  void eachOwnerOfACoveredLinkIsWarnedOnceInTheirLanguage() {
    when(links.findWithUrlContaining("bad.example"))
        .thenReturn(
            List.of(
                link(1L, "one111", "https://bad.example/a"),
                link(2L, "two111", "https://cdn.bad.example/x"),
                link(2L, "two222", "https://bad.example/y"),
                link(3L, "notbad", "https://notbad.example/z"),
                link(null, "anon11", "https://bad.example/anon")));
    when(users.findAll(Set.of(1L, 2L)))
        .thenReturn(
            List.of(
                new UserSummary(1L, "a", null, null, null, "en"),
                new UserSummary(2L, "b", null, null, null, "ja")));

    int warned = fanout.execute("bad.example");

    assertThat(warned).isEqualTo(2);
    WarningCopy.Copy en = WarningCopy.domainBlocked("en", "bad.example");
    WarningCopy.Copy ja = WarningCopy.domainBlocked("ja", "bad.example");
    verify(dispatcher)
        .dispatch(1L, LinkNotificationType.WARNING, "one111", en.subtitle(), en.body());
    verify(dispatcher)
        .dispatch(
            eq(2L), eq(LinkNotificationType.WARNING), isNull(), eq(ja.subtitle()), eq(ja.body()));
    verify(dispatcher, never()).dispatch(eq(3L), any(), any(), anyString(), anyString());
  }

  @Test
  void noCoveredLinkMeansNoLookupOfOwners() {
    when(links.findWithUrlContaining("bad.example"))
        .thenReturn(List.of(link(3L, "notbad", "https://notbad.example/z")));

    assertThat(fanout.execute("bad.example")).isZero();
    verify(users, never()).findAll(any());
  }
}
