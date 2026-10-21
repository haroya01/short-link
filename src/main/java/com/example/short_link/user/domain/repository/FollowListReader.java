package com.example.short_link.user.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FollowListReader {

  enum Side {
    FOLLOWERS,
    FOLLOWING
  }

  record Entry(boolean remote, Long id) {}

  // Serialized beside federation's RemoteAccountView, which clients decode with the same type.
  record RemoteAccount(
      Long id,
      String acct,
      String username,
      String domain,
      String displayName,
      String avatarUrl,
      String url,
      boolean following,
      boolean requested,
      boolean domainBlocked,
      boolean blocking,
      boolean muting) {}

  record Counts(long followers, long following, long remoteFollowers, long remoteFollowing) {}

  List<Entry> page(Long userId, Side side, Long viewerId, int offset, int limit);

  Map<Long, RemoteAccount> remoteAccounts(Collection<Long> remoteActorIds, Long viewerId);

  Counts counts(Long userId);
}
