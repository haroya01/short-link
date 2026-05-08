package com.example.short_link.link.application;

import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.maxmind.geoip2.model.CityResponse;
import java.net.InetAddress;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpResolver {

  private final GeoIpDatabaseHolder holder;

  public GeoLocation resolve(String ip) {
    if (ip == null || ip.isBlank()) {
      return GeoLocation.empty();
    }
    try {
      InetAddress address = InetAddress.getByName(ip);
      Optional<CityResponse> city = holder.get().tryCity(address);
      return city.map(this::toLocation).orElse(GeoLocation.empty());
    } catch (Exception e) {
      log.debug("geoip lookup failed for {}: {}", ip, e.toString());
      return GeoLocation.empty();
    }
  }

  public String resolveCountry(String ip) {
    return resolve(ip).countryCode();
  }

  private GeoLocation toLocation(CityResponse r) {
    String country = r.getCountry() != null ? r.getCountry().getIsoCode() : null;
    String region =
        r.getMostSpecificSubdivision() != null ? r.getMostSpecificSubdivision().getName() : null;
    String city = r.getCity() != null ? r.getCity().getName() : null;
    return new GeoLocation(country, region, city);
  }
}
