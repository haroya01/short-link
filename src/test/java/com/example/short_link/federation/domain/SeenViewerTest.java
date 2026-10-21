package com.example.short_link.federation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeenViewerTest {

  private static final String DEVICE = "6f1c2a3b-4d5e-4f60-8a71-92b3c4d5e6f7";

  @Test
  void aMemberIsTheirIdEvenWhenTheirDeviceSaysWhoItIs() {
    SeenViewer member = SeenViewer.of(7L, DEVICE);

    assertThat(member.kind()).isEqualTo(SeenViewer.MEMBER);
    assertThat(HexFormat.of().formatHex(member.keyBytes()))
        .isEqualTo("00000000000000000000000000000007");
  }

  @Test
  void aSignedOutReaderIsTheirDevicesRandomUuid() {
    SeenViewer device = SeenViewer.of(null, DEVICE.toUpperCase());

    assertThat(device.kind()).isEqualTo(SeenViewer.DEVICE);
    assertThat(device.key()).isEqualTo(UUID.fromString(DEVICE));
    assertThat(HexFormat.of().formatHex(device.keyBytes())).isEqualTo(DEVICE.replace("-", ""));
  }

  @Test
  void aDeviceHeaderThatIsNotARandomUuidTellsNobodyApart() {
    assertThat(SeenViewer.of(null, null)).isNull();
    assertThat(SeenViewer.of(null, "")).isNull();
    assertThat(SeenViewer.of(null, "device-1")).isNull();
    assertThat(SeenViewer.of(null, "6f1c2a3b-4d5e-1f60-8a71-92b3c4d5e6f7")).isNull();
    assertThat(SeenViewer.of(null, "6f1c2a3b-4d5e-4f60-7a71-92b3c4d5e6f7")).isNull();
    assertThat(SeenViewer.of(null, DEVICE + "0")).isNull();
    assertThat(SeenViewer.of(null, "6f1c2a3b4d5e4f608a7192b3c4d5e6f7")).isNull();
  }

  @Test
  void aPostIsTheFirstSixteenBytesOfTheSha256OfItsUri() {
    SeenUri uri = SeenUri.of("https://mastodon.social/users/a/statuses/1");

    assertThat(HexFormat.of().formatHex(uri.bytes())).isEqualTo("04993fcd865444ec3268dfa316f9df06");
    assertThat(SeenUri.fromBytes(uri.bytes())).isEqualTo(uri);
  }
}
