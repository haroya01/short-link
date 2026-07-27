package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.AsnRawInfo;
import com.example.short_link.common.geoip.GeoLookup;
import org.junit.jupiter.api.Test;

class AsnResolverTest {

  @Test
  void resolveReturnsEmptyWhenLookupEmpty() {
    GeoLookup lookup = mock(GeoLookup.class);
    when(lookup.lookupAsn("8.8.8.8")).thenReturn(AsnRawInfo.empty());

    AsnResolver.AsnInfo info = new AsnResolver(lookup).resolve("8.8.8.8");

    assertThat(info.asn()).isNull();
    assertThat(info.organization()).isNull();
    assertThat(info.datacenter()).isFalse();
  }

  @Test
  void resolveMarksAwsAsDatacenter() {
    GeoLookup lookup = mock(GeoLookup.class);
    when(lookup.lookupAsn("52.94.236.1")).thenReturn(new AsnRawInfo(16509, "Amazon.com, Inc."));

    AsnResolver.AsnInfo info = new AsnResolver(lookup).resolve("52.94.236.1");

    assertThat(info.asn()).isEqualTo(16509);
    assertThat(info.organization()).isEqualTo("Amazon.com, Inc.");
    assertThat(info.datacenter()).isTrue();
  }

  @Test
  void resolveDoesNotMarkUnknownAsnAsDatacenter() {
    GeoLookup lookup = mock(GeoLookup.class);
    when(lookup.lookupAsn("73.0.0.1")).thenReturn(new AsnRawInfo(7922, "Comcast"));

    AsnResolver.AsnInfo info = new AsnResolver(lookup).resolve("73.0.0.1");

    assertThat(info.asn()).isEqualTo(7922);
    assertThat(info.datacenter()).isFalse();
    assertThat(info.relay()).isFalse();
  }

  /**
   * iCloud Private Relay exits through Cloudflare. Marking that as datacenter deleted every iPhone
   * reader on Private Relay from the human numbers — the reported "someone opened my link and the
   * stats didn't move". Relay egress is a person whose network is hidden, not cloud traffic.
   */
  @Test
  void resolveMarksCloudflareAsRelayNotDatacenter() {
    GeoLookup lookup = mock(GeoLookup.class);
    when(lookup.lookupAsn("104.28.0.1")).thenReturn(new AsnRawInfo(13335, "Cloudflare, Inc."));

    AsnResolver.AsnInfo info = new AsnResolver(lookup).resolve("104.28.0.1");

    assertThat(info.relay()).isTrue();
    assertThat(info.datacenter()).isFalse();
  }

  @Test
  void resolveMarksFastlyAsRelayNotDatacenter() {
    GeoLookup lookup = mock(GeoLookup.class);
    when(lookup.lookupAsn("151.101.0.1")).thenReturn(new AsnRawInfo(54113, "Fastly, Inc."));

    AsnResolver.AsnInfo info = new AsnResolver(lookup).resolve("151.101.0.1");

    assertThat(info.relay()).isTrue();
    assertThat(info.datacenter()).isFalse();
  }

  /**
   * The two sets must stay disjoint — an ASN in both would make the click verdict order-dependent.
   */
  @Test
  void datacenterAndRelaySetsAreDisjoint() {
    assertThat(AsnResolver.DATACENTER_ASN).doesNotContainAnyElementsOf(AsnResolver.RELAY_ASN);
  }
}
