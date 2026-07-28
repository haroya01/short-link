package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ChannelDepthRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HostFirstSeenRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;

public interface ClickLifecycleReadRepository {

  ReturnRateRow findReturnRate(Long linkId);

  List<DayClickRow> findLifecycleClicks(Long linkId, int maxDay);

  List<HostFirstSeenRow> findFirstSeenByReferrerHost(Long linkId);

  List<ChannelDepthRow> findChannelDepth(Long linkId, int limit);
}
