package com.example.short_link.post.application.image;

import com.example.short_link.common.image.ImageVariantsRequested;
import com.example.short_link.common.storage.ImageRejections;
import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ImageUploads;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.common.storage.PresignedUpload;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostImageService {

  private static final String KEY_PREFIX = "post-images/";

  private static final ImageRejections REJECTIONS =
      new ImageRejections(
          () -> new UserException(UserErrorCode.AVATAR_UNAVAILABLE),
          allowed ->
              new PostException(
                  PostErrorCode.PERMISSION_DENIED, "contentType must be one of: " + allowed),
          () -> new PostException(PostErrorCode.PERMISSION_DENIED, "key not owned by user/post"),
          () -> new PostException(PostErrorCode.PERMISSION_DENIED, "upload not found"),
          (size, max) ->
              new PostException(
                  PostErrorCode.PERMISSION_DENIED,
                  "image exceeds maxBytes (" + size + " > " + max + ")"));

  private final ImageUploads uploads;
  private final ImageUploadPolicy uploadPolicy;
  private final ObjectStorage objectStorage;
  private final PostOwnership postOwnership;
  private final ExternalPostImageReader externalImages;
  private final ObjectStoragePublicUrls publicUrls;
  private final ApplicationEventPublisher events;

  public PresignedUpload presignUpload(Long userId, Long postId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    uploads.requireConfigured(REJECTIONS);
    postOwnership.verifyOwned(userId, postId);
    return uploads.presign(
        ownedPrefix(userId, postId), contentType, ImageUploads.STILL_OR_GIF, REJECTIONS);
  }

  public CommitResult commitUpload(Long userId, Long postId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    uploads.requireConfigured(REJECTIONS);
    postOwnership.verifyOwned(userId, postId);
    String publicUrl = uploads.commitWithVariants(ownedPrefix(userId, postId), key, REJECTIONS);
    events.publishEvent(ImageVariantsRequested.of(key));
    return new CommitResult(publicUrl, key);
  }

  public CommitResult importFromUrl(Long userId, Long postId, String url) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    uploads.requireConfigured(REJECTIONS);
    postOwnership.verifyOwned(userId, postId);

    ExternalPostImageReader.Image image = externalImages.read(url, uploadPolicy.maxBytes(), postId);
    String contentType = image.contentType();
    byte[] body = image.body();

    String key =
        ownedPrefix(userId, postId)
            + UUID.randomUUID()
            + "."
            + ImageUploads.STILL_OR_GIF.get(contentType);
    objectStorage.putObject(key, contentType, body);
    events.publishEvent(ImageVariantsRequested.of(key));
    return new CommitResult(publicUrls.forKey(key), key);
  }

  private static String ownedPrefix(Long userId, Long postId) {
    return KEY_PREFIX + userId + "/" + postId + "/";
  }

  public record CommitResult(String imageUrl, String key) {}
}
