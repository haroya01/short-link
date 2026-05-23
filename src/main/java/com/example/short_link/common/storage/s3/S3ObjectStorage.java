package com.example.short_link.common.storage.s3;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.user.application.avatar.AvatarProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

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
