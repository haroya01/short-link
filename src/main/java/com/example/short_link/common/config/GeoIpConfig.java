package com.example.short_link.common.config;

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

  private final GeoIpDatabaseHolder holder;

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
  }
}
