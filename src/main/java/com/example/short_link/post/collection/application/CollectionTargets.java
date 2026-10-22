package com.example.short_link.post.collection.application;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.domain.PostEntity;

public final class CollectionTargets {

  private CollectionTargets() {}

  public static boolean shows(PostEntity post, Long curatorId) {
    return !post.isInRoom()
        && (PostAccessPolicy.isOpen(post) || (curatorId != null && post.isOwnedBy(curatorId)));
  }
}
