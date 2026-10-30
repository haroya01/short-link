package com.example.short_link.user.application.write.avatar;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ImageUploads;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.UserFolder;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BannerService extends UserImageUpload {

  public BannerService(
      UserRepository userRepository,
      ImageUploads uploads,
      ObjectStorage objectStorage,
      ProfileCacheInvalidator cacheEviction,
      TransactionTemplate transaction) {
    super(
        UserFolder.BANNERS,
        "banner",
        userRepository,
        uploads,
        objectStorage,
        cacheEviction,
        transaction);
  }

  public CommitResult commitUpload(Long userId, String key) {
    return new CommitResult(commit(userId, key));
  }

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
