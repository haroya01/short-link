package com.example.short_link.common.config;

import com.example.short_link.common.geoip.AsnDatabaseHolder;
import com.example.short_link.common.geoip.GeoIpDatabaseHolder;
import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GeoIpConfig {

  @Value("classpath:geoip/GeoLite2-City.mmdb")
  private Resource database;

  @Value("classpath:geoip/GeoLite2-City-Fallback.mmdb")
  private Resource fallbackDatabase;

  @Value("classpath:geoip/GeoLite2-ASN.mmdb")
  private Resource asnDatabase;

  private final GeoIpDatabaseHolder holder;
  private final AsnDatabaseHolder asnHolder;

  @EventListener(ApplicationReadyEvent.class)
  public void seedFromBundled() throws IOException {
    Resource source = database.exists() ? database : fallbackDatabase;
    File temp = Files.createTempFile("GeoLite2-City", ".mmdb").toFile();
    temp.deleteOnExit();
    try (InputStream in = source.getInputStream();
        FileOutputStream out = new FileOutputStream(temp)) {
      in.transferTo(out);
    }
    DatabaseReader reader =
        new DatabaseReader.Builder(temp).fileMode(FileMode.MEMORY_MAPPED).build();
    holder.set(reader);
    log.info("GeoLite2 database seeded from {}", source.getDescription());

    if (asnDatabase.exists()) {
      File asnTemp = Files.createTempFile("GeoLite2-ASN", ".mmdb").toFile();
      asnTemp.deleteOnExit();
      try (InputStream in = asnDatabase.getInputStream();
          FileOutputStream out = new FileOutputStream(asnTemp)) {
        in.transferTo(out);
      }
      DatabaseReader asnReader =
          new DatabaseReader.Builder(asnTemp).fileMode(FileMode.MEMORY_MAPPED).build();
      asnHolder.set(asnReader);
      log.info("GeoLite2-ASN database seeded from {}", asnDatabase.getDescription());
    } else {
      log.info("GeoLite2-ASN not bundled — ASN resolution disabled (clicks will have null asn).");
    }
  }
}
