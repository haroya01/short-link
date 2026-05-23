package com.example.short_link.common.geoip.maxmind;

import com.example.short_link.common.geoip.AsnDatabaseHolder;
import com.example.short_link.common.geoip.AsnRawInfo;
import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.common.geoip.GeoLookup;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import java.net.InetAddress;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaxmindGeoLookup implements GeoLookup {

  private final GeoIpDatabaseHolder cityHolder;
  private final AsnDatabaseHolder asnHolder;

  @Override
  public GeoLocation lookupLocation(String ip) {
    if (ip == null || ip.isBlank()) return GeoLocation.empty();
    try {
      InetAddress address = InetAddress.getByName(ip);
      Optional<CityResponse> city = cityHolder.get().tryCity(address);
      return city.map(MaxmindGeoLookup::toLocation).orElse(GeoLocation.empty());
    } catch (Exception e) {
      log.debug("geoip lookup failed for {}: {}", ip, e.toString());
      return GeoLocation.empty();
    }
  }

  @Override
  public AsnRawInfo lookupAsn(String ip) {
    DatabaseReader reader = asnHolder.getOrNull();
    if (reader == null || ip == null || ip.isBlank()) return AsnRawInfo.empty();
    try {
      InetAddress address = InetAddress.getByName(ip);
      Optional<AsnResponse> resp = reader.tryAsn(address);
      if (resp.isEmpty()) return AsnRawInfo.empty();
      AsnResponse a = resp.get();
      Long asnLong = a.getAutonomousSystemNumber();
      Integer asn = asnLong == null ? null : asnLong.intValue();
      return new AsnRawInfo(asn, a.getAutonomousSystemOrganization());
    } catch (Exception e) {
      log.debug("ASN lookup failed for {}: {}", ip, e.toString());
      return AsnRawInfo.empty();
    }
  }

  private static GeoLocation toLocation(CityResponse r) {
    String country = r.getCountry() != null ? r.getCountry().getIsoCode() : null;
    String region =
        r.getMostSpecificSubdivision() != null ? r.getMostSpecificSubdivision().getName() : null;
    String city = r.getCity() != null ? r.getCity().getName() : null;
    return new GeoLocation(country, region, city);
  }
}
