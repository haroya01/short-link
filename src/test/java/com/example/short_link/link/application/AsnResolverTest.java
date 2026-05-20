package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.AsnDatabaseHolder;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AsnResolverTest {

  @Test
  void resolveReturnsEmptyWhenDatabaseMissing() {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    when(holder.getOrNull()).thenReturn(null);
    AsnResolver resolver = new AsnResolver(holder);

    AsnResolver.AsnInfo info = resolver.resolve("8.8.8.8");

    assertThat(info.asn()).isNull();
    assertThat(info.organization()).isNull();
    assertThat(info.datacenter()).isFalse();
  }

  @Test
  void resolveReturnsEmptyForNullOrBlankIp() {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    when(holder.getOrNull()).thenReturn(mock(DatabaseReader.class));
    AsnResolver resolver = new AsnResolver(holder);

    assertThat(resolver.resolve(null).asn()).isNull();
    assertThat(resolver.resolve("").asn()).isNull();
    assertThat(resolver.resolve("   ").asn()).isNull();
  }

  @Test
  void resolveReturnsEmptyWhenNoMatch() throws Exception {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.getOrNull()).thenReturn(reader);
    when(reader.tryAsn(any())).thenReturn(Optional.empty());
    AsnResolver resolver = new AsnResolver(holder);

    AsnResolver.AsnInfo info = resolver.resolve("1.2.3.4");
    assertThat(info.asn()).isNull();
  }

  @Test
  void resolveMarksAwsAsDatacenter() throws Exception {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.getOrNull()).thenReturn(reader);
    AsnResponse resp = mock(AsnResponse.class);
    when(resp.getAutonomousSystemNumber()).thenReturn(16509L);
    when(resp.getAutonomousSystemOrganization()).thenReturn("Amazon.com, Inc.");
    when(reader.tryAsn(any())).thenReturn(Optional.of(resp));
    AsnResolver resolver = new AsnResolver(holder);

    AsnResolver.AsnInfo info = resolver.resolve("52.94.236.1");

    assertThat(info.asn()).isEqualTo(16509);
    assertThat(info.organization()).isEqualTo("Amazon.com, Inc.");
    assertThat(info.datacenter()).isTrue();
  }

  @Test
  void resolveDoesNotMarkUnknownAsnAsDatacenter() throws Exception {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.getOrNull()).thenReturn(reader);
    AsnResponse resp = mock(AsnResponse.class);
    when(resp.getAutonomousSystemNumber()).thenReturn(7922L);
    when(resp.getAutonomousSystemOrganization()).thenReturn("Comcast");
    when(reader.tryAsn(any())).thenReturn(Optional.of(resp));
    AsnResolver resolver = new AsnResolver(holder);

    AsnResolver.AsnInfo info = resolver.resolve("73.0.0.1");

    assertThat(info.asn()).isEqualTo(7922);
    assertThat(info.datacenter()).isFalse();
  }

  @Test
  void resolveCatchesAnyExceptionAndReturnsEmpty() throws Exception {
    AsnDatabaseHolder holder = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.getOrNull()).thenReturn(reader);
    when(reader.tryAsn(any())).thenThrow(new IOException("boom"));
    AsnResolver resolver = new AsnResolver(holder);

    AsnResolver.AsnInfo info = resolver.resolve("1.1.1.1");
    assertThat(info.asn()).isNull();
  }
}
