package com.example.short_link.common.geoip;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.geoip")
public record GeoipProperties(boolean refreshEnabled, String licenseKey, String downloadUrl) {

  public GeoipProperties {
    if (licenseKey == null) licenseKey = "";
    if (downloadUrl == null) downloadUrl = "";
  }
}
