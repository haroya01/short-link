package com.example.short_link.notification.application.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.notification.domain.NotificationActor;
import java.util.List;
import org.junit.jupiter.api.Test;

class PushActorTest {

  private static final String ZWSP = String.valueOf((char) 0x200B);

  @Test
  void aMemberIsSentWithTheNameTheyChoseAndTheAvatarTheyUploaded() {
    NotificationActor alice =
        new NotificationActor(
            2L,
            "alice",
            "https://cdn.kurl.example/avatars/2/a.jpg",
            null,
            null,
            "  앨리스 ",
            List.of());

    assertThat(PushActor.of(alice, true))
        .isEqualTo(
            new PushActor(
                2L, null, "alice", "앨리스", "https://cdn.kurl.example/avatars/2/a.jpg", true));
  }

  @Test
  void anAvatarTheDeviceCouldNotLoadSafelyIsLeftOut() {
    NotificationActor plain =
        new NotificationActor(
            2L, "alice", "http://cdn.kurl.example/avatars/2/a.jpg", null, null, " ", List.of());

    assertThat(PushActor.of(plain, false))
        .isEqualTo(new PushActor(2L, null, "alice", null, null, false));
  }

  @Test
  void anAccountElsewhereIsSentWithoutTheAvatarOnItsServerAndWithItsNameAsPlainText() {
    NotificationActor ged =
        new NotificationActor(
            null,
            "ged@m.example",
            "https://files.m.example/ged.png",
            "https://m.example/@ged",
            42L,
            ":new_l:" + ZWSP + " Ged " + ZWSP + ":verified:" + ZWSP + " :unknown:",
            List.of(
                new CustomEmoji("new_l", "https://files.m.example/l.png"),
                new CustomEmoji("verified", "https://files.m.example/v.png")));

    assertThat(PushActor.of(ged, true))
        .isEqualTo(new PushActor(null, 42L, "ged@m.example", "Ged :unknown:", null, true));
  }

  @Test
  void nobodyOrAMemberWithoutAUsernameHasNoFaceToShow() {
    assertThat(PushActor.of(null, true)).isNull();
    assertThat(
            PushActor.of(new NotificationActor(2L, null, "https://cdn.kurl.example/a.jpg"), true))
        .isNull();
  }

  @Test
  void aNameMadeOnlyOfEmojisIsLeftOut() {
    NotificationActor emojiOnly =
        new NotificationActor(
            null,
            "ged@m.example",
            null,
            "https://m.example/@ged",
            42L,
            ZWSP + ":blobcat:" + ZWSP,
            List.of(new CustomEmoji("blobcat", "https://files.m.example/b.png")));

    assertThat(PushActor.of(emojiOnly, false).displayName()).isNull();
  }
}
