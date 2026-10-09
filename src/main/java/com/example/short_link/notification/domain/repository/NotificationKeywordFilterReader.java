package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.policy.KeywordFilter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NotificationKeywordFilterReader {

  Map<Long, List<KeywordFilter>> activeFor(Collection<Long> userIds, Instant now);
}
