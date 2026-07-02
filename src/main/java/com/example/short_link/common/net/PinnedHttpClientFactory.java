package com.example.short_link.common.net;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

/**
 * Per-request Apache HttpClient pinned to the IPs {@link PublicHttpUrlGuard} already resolved.
 * Shared between LinkWebhookDispatcher and OgScraper — both make outbound HTTP from user-supplied
 * URLs and need the same DNS-rebinding closure: the DnsResolver returns only the pre-resolved
 * addresses, and throws UnknownHostException for any other host (which kills cross-host redirects
 * but prevents a re-resolve to a private IP).
 *
 * <p>Connection manager is per-request — not shared. Pinning per-host with a shared pool defeats
 * the intent (the pool would key by host name, but the DnsResolver pins to one specific resolved
 * batch), and bursty outbound traffic doesn't gain much from pooling.
 */
public final class PinnedHttpClientFactory {

  private PinnedHttpClientFactory() {}

  public static CloseableHttpClient build(
      Resolved resolved,
      Timeout connectTimeout,
      Timeout socketTimeout,
      Timeout responseTimeout,
      boolean followRedirects) {
    String pinnedHost = resolved.uri().getHost();
    InetAddress[] pinnedAddrs = resolved.addresses().toArray(new InetAddress[0]);
    DnsResolver pinned =
        new DnsResolver() {
          @Override
          public InetAddress[] resolve(String host) throws UnknownHostException {
            if (host != null && host.equalsIgnoreCase(pinnedHost)) {
              return pinnedAddrs;
            }
            throw new UnknownHostException("DNS pinned to " + pinnedHost + "; refused " + host);
          }

          @Override
          public String resolveCanonicalHostname(String host) {
            return host;
          }
        };
    ConnectionConfig connConfig =
        ConnectionConfig.custom()
            .setConnectTimeout(connectTimeout)
            .setSocketTimeout(socketTimeout)
            .build();
    var connMgr =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(pinned)
            .setDefaultConnectionConfig(connConfig)
            .build();
    RequestConfig reqConfig =
        RequestConfig.custom()
            .setResponseTimeout(responseTimeout)
            .setRedirectsEnabled(followRedirects)
            .build();
    return HttpClients.custom()
        .setConnectionManager(connMgr)
        .setConnectionManagerShared(false)
        .setDefaultRequestConfig(reqConfig)
        .disableAutomaticRetries()
        .build();
  }
}
