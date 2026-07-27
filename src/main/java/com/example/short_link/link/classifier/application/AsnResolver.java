package com.example.short_link.link.classifier.application;

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
   * exhaustive — extend as new abuse patterns surface. Consumer privacy relays are deliberately not
   * here — see {@link #RELAY_ASN}.
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
   * Egress networks that carry ordinary people behind a privacy relay — iCloud Private Relay (which
   * exits through Cloudflare / Fastly / Akamai) and Cloudflare WARP. These used to sit in {@link
   * #DATACENTER_ASN}, which quietly erased every iPhone reader on Private Relay from the "people"
   * numbers: the click was stored as {@code bot=true, botName="datacenter:Cloudflare"}, counted in
   * the total but not in people or unique visitors — the reported "someone opened my link and the
   * stats didn't move".
   *
   * <p>The trade is deliberate and asymmetric: some scraping does run on Cloudflare Workers, so a
   * few bots now land in the human bucket — but erasing a real reader is worse than tolerating an
   * occasional bot, and the UA classifier + burst heuristic still catch the obvious ones. Akamai
   * was never listed, so relay traffic exiting there already counted as human.
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
