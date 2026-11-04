package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.projection.ClickProjections.ChannelDepthRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReturnRateRow;
import java.util.List;

public interface ClickLifecycleReadRepository {

  ReturnRateRow findReturnRate(Long linkId);

  List<DayClickRow> findLifecycleClicks(Long linkId, int maxDay);

  List<ChannelDepthRow> findChannelDepth(Long linkId, int limit);
}
