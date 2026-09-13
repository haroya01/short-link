package com.example.short_link.common.geoip;

/**
 * Lookup failures must never throw into the click pipeline. Missing or malformed IPs return {@link
 * GeoLocation#empty()} or {@link AsnRawInfo#empty()}.
 */
public interface GeoLookup {

  GeoLocation lookupLocation(String ip);

  AsnRawInfo lookupAsn(String ip);
}
