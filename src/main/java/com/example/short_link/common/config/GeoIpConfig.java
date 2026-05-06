package com.example.short_link.common.config;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class GeoIpConfig {

  @Value("classpath:geoip/GeoLite2-Country.mmdb")
  private Resource database;

  private DatabaseReader reader;

  @Bean
  public DatabaseReader geoIpDatabaseReader() throws IOException {
    try (InputStream in = database.getInputStream()) {
      this.reader = new DatabaseReader.Builder(in).build();
      return reader;
    }
  }

  @PreDestroy
  void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }
}
