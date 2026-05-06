package com.example.short_link.link.application;

import com.maxmind.geoip2.DatabaseReader;
import java.net.InetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GeoIpResolver {

  private final DatabaseReader reader;

  @Autowired
  public GeoIpResolver(DatabaseReader reader) {
    this.reader = reader;
  }

  public String resolveCountry(String ip) {
    if (reader == null || ip == null || ip.isBlank()) {
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
