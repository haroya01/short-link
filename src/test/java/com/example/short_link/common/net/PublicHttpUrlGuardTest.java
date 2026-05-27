package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
    // fc00::/7 — both fc and fd prefixes.
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
}
