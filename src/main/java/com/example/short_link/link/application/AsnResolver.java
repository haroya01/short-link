package com.example.short_link.link.application;

import com.example.short_link.common.geoip.AsnRawInfo;
import com.example.short_link.common.geoip.GeoLookup;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Looks up the autonomous system number + organisation for an IP. Used in the click pipeline so
 * stats can group "real visitors" vs "datacenter / cloud egress" without each consumer having to
 * carry a list of cloud AS numbers themselves.
 */
@Service
@RequiredArgsConstructor
public class AsnResolver {

  /**
   * Coarse list of cloud / hosting AS numbers we treat as "non-eyeball traffic" for stats. Not
   * exhaustive — extend as new abuse patterns surface.
   */
  static final Set<Integer> DATACENTER_ASN =
      Set.of(
          16509, // AWS
          14618, // AWS-2
          15169, // Google
          396982, // Google Cloud
          8075, // Microsoft / Azure
          14061, // DigitalOcean
          16276, // OVH
          24940, // Hetzner
          63949, // Linode
          20473, // Choopa / Vultr
          13335, // Cloudflare
          54113, // Fastly
          14907, // Wikimedia
          32934, // Facebook
          32590, // Valve
          396356, // Twitter / X
          19551 // Incapsula
          );

  private final GeoLookup geoLookup;

  public AsnInfo resolve(String ip) {
    AsnRawInfo raw = geoLookup.lookupAsn(ip);
    boolean isDatacenter = raw.asn() != null && DATACENTER_ASN.contains(raw.asn());
    return new AsnInfo(raw.asn(), raw.organization(), isDatacenter);
  }

  public record AsnInfo(Integer asn, String organization, boolean datacenter) {
    public static AsnInfo empty() {
      return new AsnInfo(null, null, false);
    }
  }
}
