package com.example.short_link.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.storage.s3.S3StorageConfig;
import com.example.short_link.common.storage.s3.S3StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ImageStorageSettingsTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(StorageSettings.class);

  @ParameterizedTest
  @CsvSource({"0, 0", "-1, -1"})
  void nonPositiveUploadLimitsRetainTheirDefaults(long ttl, long maxBytes) {
    ImageUploadPolicy policy = new ImageUploadPolicy(ttl, maxBytes);

    assertThat(policy.presignTtlSeconds()).isEqualTo(300);
    assertThat(policy.maxBytes()).isEqualTo(5L * 1024 * 1024);
  }

  @Test
  void configuredStorageStillRequiresBothBucketAndRegion() {
    assertThat(new S3StorageProperties("b", "r", null).isConfigured()).isTrue();
    assertThat(new S3StorageProperties("", "r", null).isConfigured()).isFalse();
    assertThat(new S3StorageProperties("b", "", null).isConfigured()).isFalse();
    assertThat(new S3StorageProperties(null, "r", null).isConfigured()).isFalse();
    assertThat(new S3StorageProperties("b", null, null).isConfigured()).isFalse();
  }

  @Test
  void legacyPropertyKeysBindTheTwoResponsibilitiesWithoutChangingValues() {
    contextRunner
        .withPropertyValues(
            "short-link.avatar.bucket=legacy-bucket",
            "short-link.avatar.region=ap-northeast-2",
            "short-link.avatar.public-base-url=https://cdn.example.test/",
            "short-link.avatar.presign-ttl-seconds=600",
            "short-link.avatar.max-bytes=10485760")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(S3StorageProperties.class);
              assertThat(context).hasSingleBean(ImageUploadPolicy.class);
              assertThat(context.getBean(S3StorageProperties.class))
                  .isEqualTo(
                      new S3StorageProperties(
                          "legacy-bucket", "ap-northeast-2", "https://cdn.example.test/"));
              assertThat(context.getBean(ImageUploadPolicy.class))
                  .isEqualTo(new ImageUploadPolicy(600, 10485760));
              assertThat(
                      context
                          .getBean(ObjectStoragePublicUrls.class)
                          .forKey("post-images/1/2/x.png"))
                  .isEqualTo("https://cdn.example.test/post-images/1/2/x.png");
            });
  }

  @Test
  void legacyEnvironmentPlaceholdersInApplicationYamlStillPopulateBothSettings() {
    contextRunner
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=classpath:/application.yml",
            "AVATAR_S3_BUCKET=environment-bucket",
            "AVATAR_S3_REGION=eu-west-1",
            "AVATAR_S3_PUBLIC_BASE_URL=https://images.example.test",
            "AVATAR_PRESIGN_TTL=900",
            "AVATAR_MAX_BYTES=2097152")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(S3StorageProperties.class))
                  .isEqualTo(
                      new S3StorageProperties(
                          "environment-bucket", "eu-west-1", "https://images.example.test"));
              assertThat(context.getBean(ImageUploadPolicy.class))
                  .isEqualTo(new ImageUploadPolicy(900, 2097152));
            });
  }

  @Test
  void missingRegionKeepsSdkFallbackButDoesNotEnableUploads() {
    contextRunner
        .withUserConfiguration(S3StorageConfig.class)
        .withPropertyValues("short-link.avatar.bucket=legacy-bucket")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(S3StorageProperties.class).isConfigured()).isFalse();
              assertThat(context.getBean(S3StorageProperties.class).region()).isNull();
              assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region())
                  .isEqualTo(Region.US_EAST_1);
              assertThat(context).hasSingleBean(S3Presigner.class);
              assertThat(context.getBean(ImageUploadPolicy.class))
                  .isEqualTo(new ImageUploadPolicy(300, 5L * 1024 * 1024));
            });
  }

  @Configuration(proxyBeanMethods = false)
  @ConfigurationPropertiesScan(basePackageClasses = ImageUploadPolicy.class)
  @Import(ObjectStoragePublicUrls.class)
  static class StorageSettings {}
}
