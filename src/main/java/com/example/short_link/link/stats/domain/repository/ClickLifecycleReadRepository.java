package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DayClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReturnRateRow;
import java.util.List;

public interface ClickLifecycleReadRepository {

  ReturnRateRow findReturnRate(Long linkId);

  List<DayClickRow> findLifecycleClicks(Long linkId, int maxDay);
}
