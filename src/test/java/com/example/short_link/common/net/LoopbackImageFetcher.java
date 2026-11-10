package com.example.short_link.common.net;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class LoopbackImageFetcher {

  private LoopbackImageFetcher() {}

  public static PublicImageFetcher overPlainHttp() {
    return new PublicImageFetcher(
        url ->
            Optional.of(new Resolved(URI.create(url), List.of(InetAddress.getLoopbackAddress()))),
        "http",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5));
  }
}
