package com.example.short_link.user.application.avatar;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(AvatarProperties.class)
public class AvatarConfig {

  /**
   * Always create the beans, even when avatar isn't configured — keeps wiring simple. The service
   * checks {@link AvatarProperties#isConfigured()} before actually using them, and the SDK clients
   * are cheap to hold idle. Credentials follow the standard chain (env / instance profile / etc.).
   */
  @Bean
  public S3Client avatarS3Client(AvatarProperties props) {
    String region =
        props.region() == null || props.region().isBlank() ? "us-east-1" : props.region();
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public S3Presigner avatarS3Presigner(AvatarProperties props) {
    String region =
        props.region() == null || props.region().isBlank() ? "us-east-1" : props.region();
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
