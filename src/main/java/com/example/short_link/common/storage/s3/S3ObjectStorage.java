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

  /**
   * 이 버킷의 키는 전부 UUID 기반이라 같은 키의 내용이 바뀔 일이 없다 — 1년 + immutable 로 박아도 안전하고, 이게 없으면 CloudFront/브라우저/앱의
   * HTTP 캐시가 휴리스틱에만 의존해 갓 올린 이미지일수록 매번 재다운로드된다.
   */
  static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final S3Client s3Client;
  private final S3Presigner presigner;
  private final AvatarProperties props;

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
      // presign PUT 은 서명에 없는 헤더를 못 싣는다(넣으면 모든 클라이언트가 같은 헤더를 보내야
      // 해서 구버전 앱 업로드가 깨진다). 대신 commit 시점에 제자리 복사로 메타데이터만 바꾼다 —
      // REPLACE 는 기존 메타데이터를 통째로 갈아끼우므로 contentType 을 읽어 보존한다.
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
