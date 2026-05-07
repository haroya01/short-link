package com.example.short_link.common.config;

import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class GeoIpConfig {

  @Value("classpath:geoip/GeoLite2-City.mmdb")
  private Resource database;

  @Value("classpath:geoip/GeoLite2-City-Fallback.mmdb")
  private Resource fallbackDatabase;

  private DatabaseReader reader;

  @Bean
  public DatabaseReader geoIpDatabaseReader() throws IOException {
    Resource source = database.exists() ? database : fallbackDatabase;
    File file = File.createTempFile("GeoLite2-City", ".mmdb");
    file.deleteOnExit();
    try (InputStream in = source.getInputStream();
        FileOutputStream out = new FileOutputStream(file)) {
      in.transferTo(out);
    }
    this.reader = new DatabaseReader.Builder(file).fileMode(FileMode.MEMORY_MAPPED).build();
    return reader;
  }

  @PreDestroy
  void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }
}
