package com.example.short_link.post.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ImportedPostRepository {

  List<Long> findIdsByImportSourceId(Long importSourceId);

  Map<Long, Long> countPublishedByImportSourceIds(Collection<Long> importSourceIds);

  int updateHiddenFromDiscover(Long importSourceId, boolean hidden);
}
