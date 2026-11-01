package com.example.short_link.note.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface RemoteMediaRepository {

  boolean kept(String key);

  int attach(
      String key, String remoteUrl, String copyUrl, Collection<Long> mediaIds, Instant cachedAt);

  List<String> expired(Instant cachedBefore, int limit);

  List<String> orphaned(int limit);

  int detach(String key);

  void forget(String key);
}
