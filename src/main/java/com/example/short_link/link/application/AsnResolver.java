package com.example.short_link.link.application;

import com.example.short_link.common.geoip.AsnDatabaseHolder;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import java.net.InetAddress;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Looks up the autonomous system number + organisation for an IP. Used in the click pipeline so
 * stats can group "real visitors" vs "datacenter / cloud egress" without each consumer having to
 * carry a list of cloud AS numbers themselves.
 *
 * <p>The resolver gracefully no-ops when the ASN database isn't bundled (returns {@link
 * AsnInfo#empty()}) — this lets dev environments boot without a MaxMind license.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsnResolver {

  /**
   * Coarse list of cloud / hosting AS numbers we treat as "non-eyeball traffic" for stats. Not
   * exhaustive — extend as new abuse patterns surface. Excluded sources are typically scrapers,
   * uptime monitors, and headless-browser farms running on cloud egress.
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
          14907, // Wikimedia (also datacenter-ish)
          32934, // Facebook
          32590, // Valve
          396356, // Twitter / X
          19551 // Incapsula
          );

  private final AsnDatabaseHolder holder;

  public AsnInfo resolve(String ip) {
    DatabaseReader reader = holder.getOrNull();
    if (reader == null || ip == null || ip.isBlank()) return AsnInfo.empty();
    try {
      InetAddress address = InetAddress.getByName(ip);
      Optional<AsnResponse> resp = reader.tryAsn(address);
      if (resp.isEmpty()) return AsnInfo.empty();
      AsnResponse a = resp.get();
      Long asnLong = a.getAutonomousSystemNumber();
      Integer asn = asnLong == null ? null : asnLong.intValue();
      String org = a.getAutonomousSystemOrganization();
      boolean isDatacenter = asn != null && DATACENTER_ASN.contains(asn);
      return new AsnInfo(asn, org, isDatacenter);
    } catch (Exception e) {
      log.debug("ASN lookup failed for {}: {}", ip, e.toString());
      return AsnInfo.empty();
    }
  }

  public record AsnInfo(Integer asn, String organization, boolean datacenter) {
    public static AsnInfo empty() {
      return new AsnInfo(null, null, false);
    }
  }
}
