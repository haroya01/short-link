package com.example.short_link.common.geoip;

public record GeoLocation(String countryCode, String region, String city, String regionCode) {

  public GeoLocation(String countryCode, String region, String city) {
    this(countryCode, region, city, null);
  }

  public static GeoLocation empty() {
    return new GeoLocation(null, null, null, null);
  }
}
