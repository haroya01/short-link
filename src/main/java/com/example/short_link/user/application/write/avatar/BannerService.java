package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ImageUploadPolicy;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BannerService extends UserImageUpload {

  public BannerService(
      UserRepository userRepository,
      ImageUploadPolicy uploadPolicy,
      ObjectStorage objectStorage,
      ObjectStoragePublicUrls publicUrls,
      ProfileCacheInvalidator cacheEviction) {
    super(
        "banners",
        "banner",
        userRepository,
        uploadPolicy,
        objectStorage,
        publicUrls,
        cacheEviction);
  }

  @Transactional
  public CommitResult commitUpload(Long userId, String key) {
    return new CommitResult(commit(userId, key));
  }

  @Transactional
  public void clearBanner(Long userId) {
    clear(userId);
  }

  @Override
  protected String currentKey(UserEntity user) {
    return user.getBannerKey();
  }

  @Override
  protected void replace(UserEntity user, String url, String key) {
    user.updateBanner(url, key);
  }

  public record CommitResult(String bannerUrl) {}
}
