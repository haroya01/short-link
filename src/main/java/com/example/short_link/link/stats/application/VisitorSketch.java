package com.example.short_link.link.stats.application;

import java.util.Collection;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.TgtHllType;
import org.apache.datasketches.hll.Union;

public final class VisitorSketch {

  // Changing either invalidates every sketch already stored in click_daily_visitors.
  private static final int LG_K = 12;
  private static final TgtHllType TYPE = TgtHllType.HLL_4;

  private VisitorSketch() {}

  public static byte[] of(Collection<String> visitorHashes) {
    HllSketch sketch = new HllSketch(LG_K, TYPE);
    visitorHashes.forEach(sketch::update);
    return sketch.toCompactByteArray();
  }

  public static long count(Collection<byte[]> sketches, Collection<String> visitorHashes) {
    Union union = new Union(LG_K);
    sketches.forEach(bytes -> union.update(HllSketch.heapify(bytes)));
    HllSketch recent = new HllSketch(LG_K, TYPE);
    visitorHashes.forEach(recent::update);
    union.update(recent);
    return Math.round(union.getResult(TYPE).getEstimate());
  }
}
