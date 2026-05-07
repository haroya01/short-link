package com.example.short_link.link.application;

public record GeoLocation(String countryCode, String regionName, String cityName) {

  public static GeoLocation empty() {
    return new GeoLocation(null, null, null);
  }
}
