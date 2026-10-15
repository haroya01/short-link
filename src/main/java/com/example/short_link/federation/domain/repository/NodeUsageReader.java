package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.NodeUsage;
import java.time.Instant;

public interface NodeUsageReader {

  NodeUsage read(Instant now);
}
