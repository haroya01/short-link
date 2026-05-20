package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoIpResolverMockTest {

  @Test
  void resolveBuildsLocationFromAllFieldsPresent() throws Exception {
    GeoIpDatabaseHolder holder = mock(GeoIpDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.get()).thenReturn(reader);
    Country country = mock(Country.class);
    when(country.getIsoCode()).thenReturn("KR");
    Subdivision subdivision = mock(Subdivision.class);
    when(subdivision.getName()).thenReturn("Seoul");
    City city = mock(City.class);
    when(city.getName()).thenReturn("Gangnam");
    CityResponse cityResp = mock(CityResponse.class);
    when(cityResp.getCountry()).thenReturn(country);
    when(cityResp.getMostSpecificSubdivision()).thenReturn(subdivision);
    when(cityResp.getCity()).thenReturn(city);
    when(reader.tryCity(any())).thenReturn(Optional.of(cityResp));
    GeoIpResolver resolver = new GeoIpResolver(holder);

    GeoLocation loc = resolver.resolve("8.8.8.8");

    assertThat(loc.countryCode()).isEqualTo("KR");
    assertThat(loc.regionName()).isEqualTo("Seoul");
    assertThat(loc.cityName()).isEqualTo("Gangnam");
  }

  @Test
  void resolveHandlesEmptyCityResponse() throws Exception {
    GeoIpDatabaseHolder holder = mock(GeoIpDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.get()).thenReturn(reader);
    when(reader.tryCity(any())).thenReturn(Optional.empty());
    GeoIpResolver resolver = new GeoIpResolver(holder);

    GeoLocation loc = resolver.resolve("8.8.8.8");

    assertThat(loc.countryCode()).isNull();
  }

  @Test
  void resolveTreatsNullSubdivisionAndCityAsNullFields() throws Exception {
    GeoIpDatabaseHolder holder = mock(GeoIpDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.get()).thenReturn(reader);
    CityResponse cityResp = mock(CityResponse.class);
    when(cityResp.getCountry()).thenReturn(null);
    when(cityResp.getMostSpecificSubdivision()).thenReturn(null);
    when(cityResp.getCity()).thenReturn(null);
    when(reader.tryCity(any())).thenReturn(Optional.of(cityResp));
    GeoIpResolver resolver = new GeoIpResolver(holder);

    GeoLocation loc = resolver.resolve("8.8.8.8");

    assertThat(loc.countryCode()).isNull();
    assertThat(loc.regionName()).isNull();
    assertThat(loc.cityName()).isNull();
  }

  @Test
  void resolveCatchesIOExceptionAndReturnsEmpty() throws Exception {
    GeoIpDatabaseHolder holder = mock(GeoIpDatabaseHolder.class);
    DatabaseReader reader = mock(DatabaseReader.class);
    when(holder.get()).thenReturn(reader);
    when(reader.tryCity(any())).thenThrow(new IOException("disk error"));
    GeoIpResolver resolver = new GeoIpResolver(holder);

    GeoLocation loc = resolver.resolve("8.8.8.8");

    assertThat(loc.countryCode()).isNull();
  }
}
