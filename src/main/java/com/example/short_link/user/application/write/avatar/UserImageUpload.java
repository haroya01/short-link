package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ImageRejections;
import com.example.short_link.common.storage.ImageUploads;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.PresignedUpload;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class UserImageUpload {

  private final String folder;
  private final String label;
  private final UserRepository userRepository;
  private final ImageUploads uploads;
  private final ObjectStorage objectStorage;
  private final ProfileCacheInvalidator cacheEviction;
  private final ImageRejections rejections;

  protected UserImageUpload(
      String folder,
      String label,
      UserRepository userRepository,
      ImageUploads uploads,
      ObjectStorage objectStorage,
      ProfileCacheInvalidator cacheEviction) {
    this.folder = folder;
    this.label = label;
    this.userRepository = userRepository;
    this.uploads = uploads;
    this.objectStorage = objectStorage;
    this.cacheEviction = cacheEviction;
    this.rejections =
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
                    label + " exceeds maxBytes (" + size + " > " + max + ")"));
  }

  protected abstract String currentKey(UserEntity user);

  protected abstract void replace(UserEntity user, String url, String key);

  public PresignedUpload presignUpload(Long userId, String contentType) {
    uploads.requireConfigured(rejections);
    return uploads.presign(ownedPrefix(userId), contentType, ImageUploads.STILL, rejections);
  }

  String commit(Long userId, String key) {
    uploads.requireConfigured(rejections);
    String publicUrl = uploads.commit(ownedPrefix(userId), key, rejections);
    UserEntity user = user(userId);
    String previousKey = currentKey(user);
    replace(user, publicUrl, key);
    if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)) {
      deleteQuietly(previousKey, "previous " + label);
    }
    cacheEviction.evictByUsername(user.getUsername());
    return publicUrl;
  }

  void clear(Long userId) {
    UserEntity user = user(userId);
    String previousKey = currentKey(user);
    replace(user, null, null);
    if (objectStorage.isConfigured() && previousKey != null && !previousKey.isBlank()) {
      deleteQuietly(previousKey, "cleared " + label);
    }
    cacheEviction.evictByUsername(user.getUsername());
  }

  private String ownedPrefix(Long userId) {
    return folder + "/" + userId + "/";
  }

  private UserEntity user(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }

  private void deleteQuietly(String key, String what) {
    try {
      objectStorage.delete(key);
    } catch (ObjectStorageException e) {
      log.warn("failed to delete {} key={}", what, key, e);
    }
  }
}
