package com.example.short_link.common.geoip;

public record GeoLocation(String countryCode, String region, String city) {

  public static GeoLocation empty() {
    return new GeoLocation(null, null, null);
  }
}
