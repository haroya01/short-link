package com.example.short_link.common.geoip.scheduler;

import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.example.short_link.common.geoip.GeoipProperties;
import com.example.short_link.common.lock.RedisDistributedLock;
import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically pulls a fresh GeoLite2-City mmdb from MaxMind and hot-swaps the in-memory database
 * via {@link GeoIpDatabaseHolder}. Skips when no license key is configured (the bundled fallback
 * mmdb keeps working). Single-instance fenced via Redis lock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoIpRefreshJob {

  private static final String LOCK_KEY = "kurl:geoip:refresh";
  private static final String DOWNLOAD_URL_TEMPLATE =
      "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz";

  private final GeoIpDatabaseHolder holder;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final GeoipProperties geoip;

  // Reused across runs — a fresh HttpClient per invocation leaks its internal selector/executor
  // threads, which accumulate over the job's weekly lifetime. (Field initializer, so it stays out
  // of the @RequiredArgsConstructor.)
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  @Scheduled(cron = "${short-link.geoip.refresh-cron:0 15 5 * * WED}", zone = "Asia/Seoul")
  public void refreshWeekly() {
    if (!geoip.refreshEnabled()) return;
    if (geoip.licenseKey().isBlank() && geoip.downloadUrl().isBlank()) {
      log.debug("geoip refresh skipped — no license key or override URL configured");
      return;
    }
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(10))) {
      log.debug("geoip refresh skipped — lock held by another instance");
      return;
    }
    try {
      Path mmdb = downloadAndExtract();
      DatabaseReader reader =
          new DatabaseReader.Builder(mmdb.toFile()).fileMode(FileMode.MEMORY_MAPPED).build();
      holder.set(reader);
      meterRegistry.counter("geoip.refresh", "result", "ok").increment();
      log.info("geoip database refreshed from MaxMind");
    } catch (Exception e) {
      meterRegistry.counter("geoip.refresh", "result", "failed").increment();
      log.warn("geoip refresh failed; existing database kept", e);
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  private Path downloadAndExtract() throws IOException, InterruptedException {
    String url =
        geoip.downloadUrl().isBlank()
            ? String.format(DOWNLOAD_URL_TEMPLATE, geoip.licenseKey())
            : geoip.downloadUrl();
    Path tmpArchive = Files.createTempFile("geolite2-", ".tar.gz");
    tmpArchive.toFile().deleteOnExit();
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
    HttpResponse<Path> response =
        httpClient.send(
            req,
            HttpResponse.BodyHandlers.ofFile(
                tmpArchive,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING));
    if (response.statusCode() / 100 != 2) {
      throw new IOException("MaxMind download HTTP " + response.statusCode());
    }
    Path mmdb = Files.createTempFile("GeoLite2-City-", ".mmdb");
    mmdb.toFile().deleteOnExit();
    try (InputStream raw = Files.newInputStream(tmpArchive);
        GzipCompressorInputStream gz = new GzipCompressorInputStream(raw);
        TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        if (entry.getName().endsWith("GeoLite2-City.mmdb")) {
          Files.copy(tar, mmdb, StandardCopyOption.REPLACE_EXISTING);
          return mmdb;
        }
      }
    } finally {
      Files.deleteIfExists(tmpArchive);
    }
    throw new IOException("GeoLite2-City.mmdb not found inside archive");
  }
}
