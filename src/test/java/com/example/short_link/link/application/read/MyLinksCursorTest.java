package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.dto.MyLinksCursor;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MyLinksCursorTest {

  @Test
  void roundTripsExactInstantAndId() {
    Instant t = Instant.parse("2026-05-13T08:14:32.123456Z");
    MyLinksCursor in = new MyLinksCursor(t, 4242L);
    MyLinksCursor out = MyLinksCursor.decode(in.encode());
    assertThat(out.createdAt()).isEqualTo(t);
    assertThat(out.id()).isEqualTo(4242L);
    assertThat(out.sortValue()).isNull();
  }

  @Test
  void roundTripsComputedSortValue() {
    Instant t = Instant.parse("2026-05-13T08:14:32.123456Z");
    MyLinksCursor in = new MyLinksCursor(t, 4242L, 17L);
    MyLinksCursor out = MyLinksCursor.decode(in.encode());
    assertThat(out.createdAt()).isEqualTo(t);
    assertThat(out.id()).isEqualTo(4242L);
    assertThat(out.sortValue()).isEqualTo(17L);
  }

  @Test
  void roundTripsAtMicrosecondBoundary() {
    // micros-only granularity is the wire contract — sub-microsecond nanos get truncated. Verify
    // an instant aligned on a microsecond boundary survives the round trip without rounding.
    Instant t = Instant.ofEpochSecond(1_700_000_000L, 123_456_000L);
    MyLinksCursor out = MyLinksCursor.decode(new MyLinksCursor(t, 1L).encode());
    assertThat(out.createdAt()).isEqualTo(t);
  }

  @Test
  void encodeIsUrlSafeBase64WithoutPadding() {
    // Cursors travel as query params — no '+', no '/', no '=' allowed without escaping.
    String encoded = new MyLinksCursor(Instant.ofEpochSecond(1L), 1L).encode();
    assertThat(encoded).doesNotContain("+").doesNotContain("/").doesNotContain("=");
  }

  @Test
  void decodeNullOrBlankReturnsNull() {
    // null/blank means "first page" — caller treats null cursor as no narrowing.
    assertThat(MyLinksCursor.decode(null)).isNull();
    assertThat(MyLinksCursor.decode("")).isNull();
    assertThat(MyLinksCursor.decode("   ")).isNull();
  }

  @Test
  void decodeGarbageThrowsIllegalArgument() {
    // Controller maps IAE to 400 — clients sending a tampered cursor get rejected, not a 500.
    assertThatThrownBy(() -> MyLinksCursor.decode("not-base64-!!!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeBase64WithoutColonThrows() {
    // Wire shape is "<micros>:<id>" — base64 of something without the separator is malformed.
    String noColon = Base64.getUrlEncoder().withoutPadding().encodeToString("123".getBytes());
    assertThatThrownBy(() -> MyLinksCursor.decode(noColon))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("malformed");
  }

  @Test
  void decodeBase64WithTrailingColonThrows() {
    String trailing = Base64.getUrlEncoder().withoutPadding().encodeToString("123:".getBytes());
    assertThatThrownBy(() -> MyLinksCursor.decode(trailing))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeBase64WithLeadingColonThrows() {
    String leading = Base64.getUrlEncoder().withoutPadding().encodeToString(":123".getBytes());
    assertThatThrownBy(() -> MyLinksCursor.decode(leading))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeBase64WithNonNumericFieldsThrows() {
    String badNum = Base64.getUrlEncoder().withoutPadding().encodeToString("abc:def".getBytes());
    assertThatThrownBy(() -> MyLinksCursor.decode(badNum))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encodesNegativeIdsRoundTrip() {
    // ids are positive in practice but the encoding shouldn't choke on Long.MIN_VALUE either —
    // protect against future synthetic cursors / test fixtures.
    MyLinksCursor in = new MyLinksCursor(Instant.ofEpochSecond(0L), Long.MIN_VALUE);
    MyLinksCursor out = MyLinksCursor.decode(in.encode());
    assertThat(out.id()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void encodesEpochZero() {
    MyLinksCursor in = new MyLinksCursor(Instant.EPOCH, 1L);
    MyLinksCursor out = MyLinksCursor.decode(in.encode());
    assertThat(out.createdAt()).isEqualTo(Instant.EPOCH);
    assertThat(out.id()).isEqualTo(1L);
  }

  @Test
  void differentInstantsProduceDifferentCursors() {
    // Sanity: cursors are deterministic and unique per (createdAt, id) — required so the next-page
    // resume predicate is stable across re-requests.
    String a = new MyLinksCursor(Instant.parse("2026-05-13T00:00:00Z"), 1L).encode();
    String b = new MyLinksCursor(Instant.parse("2026-05-13T00:00:01Z"), 1L).encode();
    String c = new MyLinksCursor(Instant.parse("2026-05-13T00:00:00Z"), 2L).encode();
    assertThat(a).isNotEqualTo(b).isNotEqualTo(c);
    assertThat(b).isNotEqualTo(c);
  }
}
