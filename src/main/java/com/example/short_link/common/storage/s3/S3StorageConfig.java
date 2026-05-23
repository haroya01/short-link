package com.example.short_link.common.storage.s3;

import com.example.short_link.user.application.avatar.AvatarProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 SDK beans for {@link S3ObjectStorage}. Always create them — the wrapper falls back to
 * unconfigured (via {@link AvatarProperties#isConfigured()}) when bucket is blank, so callers can
 * degrade gracefully without missing beans breaking startup.
 */
@Configuration
public class S3StorageConfig {

  @Bean
  public S3Client s3Client(AvatarProperties props) {
    String region =
        props.region() == null || props.region().isBlank() ? "us-east-1" : props.region();
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(AvatarProperties props) {
    String region =
        props.region() == null || props.region().isBlank() ? "us-east-1" : props.region();
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
