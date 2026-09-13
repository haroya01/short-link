package com.example.short_link.common.storage.s3;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

  /** UUID 기반 키는 덮어쓰지 않으므로 1년 immutable 캐시를 적용할 수 있다. */
  static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final S3StorageProperties props;

  @Override
  public boolean isConfigured() {
    return props.isConfigured();
  }

  @Override
  public String presignPut(String key, String contentType, Duration ttl) {
    try {
      PutObjectRequest put =
          PutObjectRequest.builder()
              .bucket(props.bucket())
              .key(key)
              .contentType(contentType)
              .build();
      PutObjectPresignRequest presign =
          PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build();
      PresignedPutObjectRequest signed = presigner.presignPutObject(presign);
      return signed.url().toString();
    } catch (RuntimeException e) {
      throw new ObjectStorageException("S3 presign failed for key=" + key, e);
    }
  }

  @Override
  public void putObject(String key, String contentType, byte[] body) {
    try {
      PutObjectRequest put =
          PutObjectRequest.builder()
              .bucket(props.bucket())
              .key(key)
              .contentType(contentType)
              .contentLength((long) body.length)
              .cacheControl(IMMUTABLE_CACHE_CONTROL)
              .build();
      s3Client.putObject(put, RequestBody.fromBytes(body));
    } catch (RuntimeException e) {
      throw new ObjectStorageException("S3 putObject failed for key=" + key, e);
    }
  }

  @Override
  public void applyImmutableCacheControl(String key) {
    try {
      // 서명 헤더를 추가하면 구버전 클라이언트 업로드가 깨지므로 commit 때 메타데이터를 바꾼다.
      // REPLACE는 메타데이터 전체를 교체하므로 기존 contentType을 보존한다.
      HeadObjectResponse head =
          s3Client.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
      CopyObjectRequest copy =
          CopyObjectRequest.builder()
              .sourceBucket(props.bucket())
              .sourceKey(key)
              .destinationBucket(props.bucket())
              .destinationKey(key)
              .metadataDirective(MetadataDirective.REPLACE)
              .contentType(head.contentType())
              .cacheControl(IMMUTABLE_CACHE_CONTROL)
              .build();
      s3Client.copyObject(copy);
    } catch (RuntimeException e) {
      log.warn("failed to apply cache-control key={}", key, e);
    }
  }

  @Override
  public Optional<Long> objectSize(String key) {
    try {
      HeadObjectResponse head =
          s3Client.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build());
      return Optional.ofNullable(head.contentLength());
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  @Override
  public void delete(String key) {
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
    } catch (RuntimeException e) {
      throw new ObjectStorageException("S3 delete failed for key=" + key, e);
    }
  }
}
