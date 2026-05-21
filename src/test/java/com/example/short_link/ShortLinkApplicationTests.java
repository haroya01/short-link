package com.example.short_link;

import io.queryaudit.junit5.QueryAudit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@QueryAudit
class ShortLinkApplicationTests {

  @Test
  void contextLoads() {}
}
