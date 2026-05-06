package com.example.short_link.link.application;

import com.maxmind.geoip2.DatabaseReader;
import java.net.InetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpResolver {

  private final DatabaseReader reader;

  public String resolveCountry(String ip) {
    if (ip == null || ip.isBlank()) {
      return null;
    }
    try {
      InetAddress address = InetAddress.getByName(ip);
      return reader.tryCountry(address).map(r -> r.getCountry().getIsoCode()).orElse(null);
    } catch (Exception e) {
      log.debug("geoip lookup failed for {}: {}", ip, e.toString());
      return null;
    }
  }
}
