package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet6Address;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PublicHttpUrlGuardTest {

  @Test
  void rejectsLoopback() {
    assertThat(PublicHttpUrlGuard.isPublic("http://127.0.0.1/x")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://localhost/")).isFalse();
  }

  @Test
  void rejectsRfc1918PrivateRanges() {
    assertThat(PublicHttpUrlGuard.isPublic("http://10.0.0.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://192.168.1.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://172.16.0.1/")).isFalse();
  }

  @Test
  void rejectsLinkLocalAndCloudMetadata() {
    assertThat(PublicHttpUrlGuard.isPublic("http://169.254.169.254/latest/meta-data")).isFalse();
  }

  @Test
  void rejectsRfc6598CarrierGradeNat() {
    // 100.64.0.0/10 — shared address space, must not be reachable from a public-URL guard.
    assertThat(PublicHttpUrlGuard.isPublic("http://100.64.0.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://100.127.255.254/")).isFalse();
    // 100.63.x and 100.128.x sit just outside the /10 — must not be over-rejected.
    assertThat(PublicHttpUrlGuard.isPublic("http://100.63.0.1/")).isTrue();
    assertThat(PublicHttpUrlGuard.isPublic("http://100.128.0.1/")).isTrue();
  }

  @Test
  void rejectsIpv6UniqueLocal() {
    assertThat(PublicHttpUrlGuard.isPublic("http://[fc00::1]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[fd12:3456:789a::1]/")).isFalse();
  }

  @Test
  void rejectsIpv4MappedIpv6PrivateAddress() {
    // ::ffff:10.0.0.1 must not slip past — the IPv4 unwrap path needs to re-check the inner v4.
    assertThat(PublicHttpUrlGuard.isPublic("http://[::ffff:10.0.0.1]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[::ffff:127.0.0.1]/")).isFalse();
  }

  @Test
  void rejectsNonHttpSchemes() {
    assertThat(PublicHttpUrlGuard.isPublic("file:///etc/passwd")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("ftp://example.com/x")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("javascript:alert(1)")).isFalse();
  }

  @Test
  void acceptsPublicHosts() {
    assertThat(PublicHttpUrlGuard.isPublic("https://www.google.com/")).isTrue();
    assertThat(PublicHttpUrlGuard.isPublic("https://github.com/haroya01")).isTrue();
  }

  @Test
  void rejectsMalformed() {
    assertThat(PublicHttpUrlGuard.isPublic(null)).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("not a url")).isFalse();
  }

  @Test
  void resolveReturnsAddressesForPublicHosts() {
    var resolved = PublicHttpUrlGuard.resolve("https://www.google.com/");
    assertThat(resolved).isPresent();
    assertThat(resolved.get().addresses()).isNotEmpty();
    assertThat(resolved.get().uri().getHost()).isEqualTo("www.google.com");
  }

  @Test
  void rejectsBlankAndNullVariants() {
    assertThat(PublicHttpUrlGuard.resolve(null)).isEmpty();
    assertThat(PublicHttpUrlGuard.resolve("   ")).isEmpty();
  }

  @Test
  void rejectsSchemelessUrl() {
    assertThat(PublicHttpUrlGuard.resolve("example.com/path")).isEmpty();
  }

  @Test
  void rejectsHostlessUrl() {
    assertThat(PublicHttpUrlGuard.resolve("https:///path")).isEmpty();
  }

  @Test
  void rejectsUnknownHostname() {
    assertThat(PublicHttpUrlGuard.resolve("https://this-host-should-not-resolve.invalid/"))
        .isEmpty();
  }

  @Test
  void rejectsIpv6Loopback() {
    assertThat(PublicHttpUrlGuard.isPublic("http://[::1]/")).isFalse();
  }

  @Test
  void rejectsMulticast() {
    assertThat(PublicHttpUrlGuard.isPublic("http://224.0.0.1/")).isFalse();
  }

  @Test
  void rejectsReservedIpv4RangesNothingPublicLivesIn() {
    assertThat(PublicHttpUrlGuard.isPublic("http://0.0.0.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://198.18.0.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://198.19.255.254/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://240.0.0.1/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://255.255.255.255/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://198.17.0.1/")).isTrue();
    assertThat(PublicHttpUrlGuard.isPublic("http://198.20.0.1/")).isTrue();
  }

  @Test
  void rejectsIpv6AddressesThatCarryAPrivateIpv4() {
    assertThat(PublicHttpUrlGuard.isPublic("http://[64:ff9b::a9fe:a9fe]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[64:ff9b::a00:1]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[64:ff9b:1::1]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[2002:a00:1::1]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[2002:7f00:1::]/")).isFalse();
    assertThat(PublicHttpUrlGuard.isPublic("http://[::a00:1]/")).isFalse();
  }

  @Test
  void acceptsIpv6AddressesThatCarryAPublicIpv4() {
    assertThat(PublicHttpUrlGuard.isPublic("http://[64:ff9b::808:808]/")).isTrue();
    assertThat(PublicHttpUrlGuard.isPublic("http://[2002:808:808::1]/")).isTrue();
  }

  @ParameterizedTest(name = "{0} private={1} ({2})")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          0.0.0.0         | true  | 0.0.0.0/8 first
          0.255.255.255   | true  | 0.0.0.0/8 last
          1.0.0.0         | false | above 0.0.0.0/8
          9.255.255.255   | false | below 10/8
          10.0.0.0        | true  | 10/8 first
          10.255.255.255  | true  | 10/8 last
          11.0.0.0        | false | above 10/8
          100.63.255.255  | false | below 100.64/10
          100.64.0.0      | true  | 100.64/10 first
          100.127.255.255 | true  | 100.64/10 last
          100.128.0.0     | false | above 100.64/10
          126.255.255.255 | false | below 127/8
          127.0.0.0       | true  | 127/8 first
          127.255.255.255 | true  | 127/8 last
          128.0.0.0       | false | above 127/8
          169.253.255.255 | false | below 169.254/16
          169.254.0.0     | true  | 169.254/16 first
          169.254.255.255 | true  | 169.254/16 last
          169.255.0.0     | false | above 169.254/16
          172.15.255.255  | false | below 172.16/12
          172.16.0.0      | true  | 172.16/12 first
          172.31.255.255  | true  | 172.16/12 last
          172.32.0.0      | false | above 172.16/12
          192.167.255.255 | false | below 192.168/16
          192.168.0.0     | true  | 192.168/16 first
          192.168.255.255 | true  | 192.168/16 last
          192.169.0.0     | false | above 192.168/16
          198.17.255.255  | false | below 198.18/15
          198.18.0.0      | true  | 198.18/15 first
          198.19.255.255  | true  | 198.18/15 last
          198.20.0.0      | false | above 198.18/15
          223.255.255.255 | false | below 224/4
          224.0.0.0       | true  | 224/4 first
          239.255.255.255 | true  | 224/4 last
          240.0.0.0       | true  | 240/4 first
          255.255.255.255 | true  | 240/4 last
          """)
  void eachIpv4BlockIsClosedFromItsFirstToItsLastAddressAndOpenJustOutside(
      String address, boolean closed, String block) throws Exception {
    assertThat(PublicHttpUrlGuard.isPrivate(InetAddress.getByName(address))).isEqualTo(closed);
  }

  @ParameterizedTest(name = "{0} private={1} ({2})")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          ::                                      | true  | unspecified
          ::1                                     | true  | loopback
          fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | false | below fc00::/7
          fc00::                                  | true  | fc00::/7 first
          fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | true  | fc00::/7 last
          fe00::                                  | false | between fc00::/7 and fe80::/10
          fe80::                                  | true  | fe80::/10 first
          febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff | true  | fe80::/10 last
          fec0::                                  | true  | fec0::/10 first
          feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | true  | fec0::/10 last
          ff00::                                  | true  | ff00::/8 first
          ff02::1                                 | true  | ff00::/8 all nodes
          2001:4860:4860::8888                    | false | global unicast
          """)
  void eachIpv6BlockIsClosedFromItsFirstToItsLastAddressAndOpenJustOutside(
      String address, boolean closed, String block) throws Exception {
    assertThat(PublicHttpUrlGuard.isPrivate(InetAddress.getByName(address))).isEqualTo(closed);
  }

  @ParameterizedTest(name = "{0} private={1} ({2})")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          2002:a00:1::                       | true  | 6to4 around 10.0.0.1
          2002:808:808::                     | false | 6to4 around 8.8.8.8
          64:ff9b::a00:1                     | true  | NAT64 well-known prefix around 10.0.0.1
          64:ff9b::808:808                   | false | NAT64 well-known prefix around 8.8.8.8
          64:ff9b:1::808:808                 | true  | local-use NAT64 /48, whatever it carries
          64:ff9b:1:ffff:ffff:ffff:ffff:ffff | true  | local-use NAT64 /48 last
          64:ff9b:2::a00:1                   | false | just above the local-use NAT64 /48
          ::a00:1                            | true  | IPv4-compatible around 10.0.0.1
          ::808:808                          | false | IPv4-compatible around 8.8.8.8
          2a00:1450::a00:1                   | false | another prefix whose last bits look like 10.0.0.1
          """)
  void anIpv6AddressCarryingAnIpv4IsJudgedByTheIpv4ItCarries(
      String address, boolean closed, String form) throws Exception {
    assertThat(PublicHttpUrlGuard.isPrivate(InetAddress.getByName(address))).isEqualTo(closed);
  }

  @ParameterizedTest(name = "::{0}:{1}.{2}.{3}.{4} private={5}")
  @CsvSource(
      textBlock =
          """
          ffff, 10, 0, 0, 1, true
          ffff, 127, 0, 0, 1, true
          ffff, 8, 8, 8, 8, false
          fffe, 10, 0, 0, 1, false
          """)
  void aMappedAddressTheResolverKeepsAsIpv6IsStillJudgedByItsIpv4(
      String marker, int a, int b, int c, int d, boolean closed) throws Exception {
    int mark = Integer.parseInt(marker, 16);
    byte[] bytes = new byte[16];
    bytes[10] = (byte) (mark >> 8);
    bytes[11] = (byte) mark;
    bytes[12] = (byte) a;
    bytes[13] = (byte) b;
    bytes[14] = (byte) c;
    bytes[15] = (byte) d;

    assertThat(PublicHttpUrlGuard.isPrivate(Inet6Address.getByAddress(null, bytes, -1)))
        .isEqualTo(closed);
  }

  @Test
  void aMappedLookingAddressWithAnythingBeforeTheMarkerIsNotUnwrapped() throws Exception {
    byte[] bytes = new byte[16];
    bytes[9] = 1;
    bytes[10] = (byte) 0xff;
    bytes[11] = (byte) 0xff;
    bytes[12] = 10;
    bytes[15] = 1;

    assertThat(PublicHttpUrlGuard.isPrivate(Inet6Address.getByAddress(null, bytes, -1))).isFalse();
  }

  @Test
  void aHostIsSaidToBeMissingOnlyWhenItsNameDoesNotResolve() {
    assertThat(PublicHttpUrlGuard.hostExists("https://this-host-should-not-resolve.invalid/"))
        .isFalse();
    assertThat(PublicHttpUrlGuard.hostExists("http://10.0.0.1/")).isTrue();
    assertThat(PublicHttpUrlGuard.hostExists("https:///path")).isTrue();
    assertThat(PublicHttpUrlGuard.hostExists("http://a b/")).isTrue();
  }
}
