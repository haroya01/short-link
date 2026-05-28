package com.example.short_link.link.application.read;

import com.example.short_link.link.domain.ShortCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LinkTagLookup {
  List<String> tagNamesFor(Long userId, ShortCode shortCode);

  Map<Long, List<String>> tagNamesByLinkIds(List<Long> linkIds);

  Optional<List<Long>> linkIdsForTag(Long userId, String tagName);
}
