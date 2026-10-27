package com.example.short_link.profile.application.image;

import com.example.short_link.common.storage.ImageRejections;
import com.example.short_link.common.storage.ImageUploads;
import com.example.short_link.common.storage.PresignedUpload;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Commit returns an image URL for a later block update without mutating the block. Uploads may
// coexist, so a new upload must not delete previous keys.
@Service
@RequiredArgsConstructor
public class ProfileImageService {

  private static final String KEY_PREFIX = "profile-images/";

  private static final ImageRejections REJECTIONS =
      new ImageRejections(
          () -> new UserException(UserErrorCode.AVATAR_UNAVAILABLE),
          allowed ->
              new UserException(
                  UserErrorCode.INVALID_AVATAR, "contentType must be one of: " + allowed),
          () -> new UserException(UserErrorCode.INVALID_AVATAR, "key not owned by user"),
          () -> new UserException(UserErrorCode.INVALID_AVATAR, "upload not found"),
          (size, max) ->
              new UserException(
                  UserErrorCode.INVALID_AVATAR,
                  "image exceeds maxBytes (" + size + " > " + max + ")"));

  private final ImageUploads uploads;

  public PresignedUpload presignUpload(Long userId, String contentType) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    uploads.requireConfigured(REJECTIONS);
    return uploads.presign(KEY_PREFIX + userId + "/", contentType, ImageUploads.STILL, REJECTIONS);
  }

  public CommitResult commitUpload(Long userId, String key) {
    if (userId == null) throw new UserException(UserErrorCode.INVALID_AVATAR, "userId required");
    uploads.requireConfigured(REJECTIONS);
    return new CommitResult(uploads.commit(KEY_PREFIX + userId + "/", key, REJECTIONS), key);
  }

  public record CommitResult(String imageUrl, String key) {}
}
