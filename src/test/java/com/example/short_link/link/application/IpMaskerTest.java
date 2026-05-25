package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.helper.IpMasker;
import org.junit.jupiter.api.Test;

class IpMaskerTest {

  @Test
  void masksIpv4LastOctet() {
    assertThat(IpMasker.mask("1.2.3.4")).isEqualTo("1.2.3.*");
  }

  @Test
  void masksIpv6FromSecondGroup() {
    assertThat(IpMasker.mask("2001:db8::1")).isEqualTo("2001:db8:*:*:*:*:*:*");
  }

  @Test
  void returnsNullForNullOrBlank() {
    assertThat(IpMasker.mask(null)).isNull();
    assertThat(IpMasker.mask("")).isNull();
    assertThat(IpMasker.mask("   ")).isNull();
  }

  @Test
  void leavesUnknownFormatAsIs() {
    assertThat(IpMasker.mask("garbage")).isEqualTo("garbage");
  }
}
