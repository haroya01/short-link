package com.example.short_link.common.geoip;

public record AsnRawInfo(Integer asn, String organization) {

  public static AsnRawInfo empty() {
    return new AsnRawInfo(null, null);
  }
}
