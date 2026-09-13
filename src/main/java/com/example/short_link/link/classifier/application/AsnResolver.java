package com.example.short_link.link.classifier.application;

import com.example.short_link.common.geoip.AsnRawInfo;
import com.example.short_link.common.geoip.GeoLookup;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsnResolver {

  /**
   * Hosting egress treated as bot traffic. Consumer privacy relays belong in {@link #RELAY_ASN}
   * instead.
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
          14907, // Wikimedia
          32934, // Facebook
          32590, // Valve
          396356, // Twitter / X
          19551 // Incapsula
          );

  /**
   * Consumer privacy relays count as people to avoid excluding real readers. This may admit some
   * cloud-hosted scraping; UA and burst heuristics still apply.
   */
  static final Set<Integer> RELAY_ASN =
      Set.of(
          13335, // Cloudflare — iCloud Private Relay egress · WARP
          54113 // Fastly — iCloud Private Relay egress
          );

  private final GeoLookup geoLookup;

  public AsnInfo resolve(String ip) {
    AsnRawInfo raw = geoLookup.lookupAsn(ip);
    boolean isDatacenter = raw.asn() != null && DATACENTER_ASN.contains(raw.asn());
    boolean isRelay = raw.asn() != null && RELAY_ASN.contains(raw.asn());
    return new AsnInfo(raw.asn(), raw.organization(), isDatacenter, isRelay);
  }

  /**
   * {@code datacenter} = hosting egress (treat as bot). {@code relay} = consumer privacy relay
   * (treat as a person whose location/network is obscured). The two are mutually exclusive.
   */
  public record AsnInfo(Integer asn, String organization, boolean datacenter, boolean relay) {
    public static AsnInfo empty() {
      return new AsnInfo(null, null, false, false);
    }
  }
}
