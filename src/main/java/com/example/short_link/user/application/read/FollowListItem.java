package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.repository.FollowListReader.RemoteAccount;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public sealed interface FollowListItem {

  @JsonTypeName("local")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Local(
      Long id,
      String username,
      String bio,
      String avatarUrl,
      Long followerCount,
      boolean followedByMe)
      implements FollowListItem {

    static Local of(FollowUserView user) {
      return new Local(
          user.id(),
          user.username(),
          user.bio(),
          user.avatarUrl(),
          user.followerCount(),
          user.followedByMe());
    }
  }

  @JsonTypeName("remote")
  record Remote(RemoteAccount account) implements FollowListItem {}
}
