package com.example.short_link.common.geoip.maxmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.AsnDatabaseHolder;
import com.example.short_link.common.geoip.AsnRawInfo;
import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.example.short_link.common.geoip.GeoLocation;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MaxmindGeoLookupTest {

  @Test
  void lookupLocationBuildsFromAllFieldsPresent() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(city.get()).thenReturn(reader);
    Country country = mock(Country.class);
    when(country.getIsoCode()).thenReturn("KR");
    Subdivision subdivision = mock(Subdivision.class);
    when(subdivision.getName()).thenReturn("Seoul");
    City cityRec = mock(City.class);
    when(cityRec.getName()).thenReturn("Gangnam");
    CityResponse cityResp = mock(CityResponse.class);
    when(cityResp.getCountry()).thenReturn(country);
    when(cityResp.getMostSpecificSubdivision()).thenReturn(subdivision);
    when(cityResp.getCity()).thenReturn(cityRec);
    when(reader.tryCity(any())).thenReturn(Optional.of(cityResp));

    GeoLocation loc = new MaxmindGeoLookup(city, asn).lookupLocation("8.8.8.8");

    assertThat(loc.countryCode()).isEqualTo("KR");
    assertThat(loc.region()).isEqualTo("Seoul");
    assertThat(loc.city()).isEqualTo("Gangnam");
  }

  @Test
  void lookupLocationHandlesEmptyCityResponse() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(city.get()).thenReturn(reader);
    when(reader.tryCity(any())).thenReturn(Optional.empty());

    GeoLocation loc = new MaxmindGeoLookup(city, asn).lookupLocation("8.8.8.8");
    assertThat(loc.countryCode()).isNull();
  }

  @Test
  void lookupLocationTreatsNullFieldsAsNull() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(city.get()).thenReturn(reader);
    CityResponse cityResp = mock(CityResponse.class);
    when(cityResp.getCountry()).thenReturn(null);
    when(cityResp.getMostSpecificSubdivision()).thenReturn(null);
    when(cityResp.getCity()).thenReturn(null);
    when(reader.tryCity(any())).thenReturn(Optional.of(cityResp));

    GeoLocation loc = new MaxmindGeoLookup(city, asn).lookupLocation("8.8.8.8");
    assertThat(loc.countryCode()).isNull();
    assertThat(loc.region()).isNull();
    assertThat(loc.city()).isNull();
  }

  @Test
  void lookupLocationCatchesIOException() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(city.get()).thenReturn(reader);
    when(reader.tryCity(any())).thenThrow(new IOException("disk error"));

    GeoLocation loc = new MaxmindGeoLookup(city, asn).lookupLocation("8.8.8.8");
    assertThat(loc.countryCode()).isNull();
  }

  @Test
  void lookupAsnReturnsEmptyWhenDatabaseMissing() {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    when(asn.getOrNull()).thenReturn(null);

    AsnRawInfo info = new MaxmindGeoLookup(city, asn).lookupAsn("8.8.8.8");
    assertThat(info.asn()).isNull();
    assertThat(info.organization()).isNull();
  }

  @Test
  void lookupAsnReturnsEmptyForNullOrBlankIp() {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    when(asn.getOrNull()).thenReturn(mock(DatabaseReader.class));
    MaxmindGeoLookup lookup = new MaxmindGeoLookup(city, asn);

    assertThat(lookup.lookupAsn(null).asn()).isNull();
    assertThat(lookup.lookupAsn("").asn()).isNull();
    assertThat(lookup.lookupAsn("   ").asn()).isNull();
  }

  @Test
  void lookupAsnReturnsRawFields() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(asn.getOrNull()).thenReturn(reader);
    AsnResponse resp = mock(AsnResponse.class);
    when(resp.getAutonomousSystemNumber()).thenReturn(16509L);
    when(resp.getAutonomousSystemOrganization()).thenReturn("Amazon.com, Inc.");
    when(reader.tryAsn(any())).thenReturn(Optional.of(resp));

    AsnRawInfo info = new MaxmindGeoLookup(city, asn).lookupAsn("52.94.236.1");
    assertThat(info.asn()).isEqualTo(16509);
    assertThat(info.organization()).isEqualTo("Amazon.com, Inc.");
  }

  @Test
  void lookupAsnCatchesIOException() throws Exception {
    GeoIpDatabaseHolder city = mock(GeoIpDatabaseHolder.class);
    AsnDatabaseHolder asn = mock(AsnDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(asn.getOrNull()).thenReturn(reader);
    when(reader.tryAsn(any())).thenThrow(new IOException("boom"));

    AsnRawInfo info = new MaxmindGeoLookup(city, asn).lookupAsn("1.1.1.1");
    assertThat(info.asn()).isNull();
  }
}
