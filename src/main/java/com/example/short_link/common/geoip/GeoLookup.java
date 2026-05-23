package com.example.short_link.common.geoip;

/**
 * Thin port for IP geo + ASN lookups. Adapter (MaxMind) is fully hidden — application sees only
 * domain records.
 *
 * <p>Adapter MUST never throw on lookup failure: a missing or malformed ip is a normal click-stream
 * event and the click pipeline can't fail. Return {@link GeoLocation#empty()} / {@link
 * AsnRawInfo#empty()} instead.
 */
public interface GeoLookup {

  GeoLocation lookupLocation(String ip);

  AsnRawInfo lookupAsn(String ip);
}
