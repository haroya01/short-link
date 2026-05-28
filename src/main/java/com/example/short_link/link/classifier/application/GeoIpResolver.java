package com.example.short_link.link.classifier.application;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.common.geoip.GeoLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoIpResolver {

  private final GeoLookup geoLookup;

  public GeoLocation resolve(String ip) {
    return geoLookup.lookupLocation(ip);
  }
}
