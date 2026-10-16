package com.example.short_link.post.application.access;

import com.example.short_link.link.classifier.application.GeoIpResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReaderCountryResolver {

  private final GeoIpResolver geoIp;

  public ReaderCountry resolve(PostReader reader) {
    if (reader.serverRender()) {
      return ReaderCountry.DEFERRED;
    }
    return ReaderCountry.of(geoIp.resolve(reader.clientIp()).countryCode());
  }
}
