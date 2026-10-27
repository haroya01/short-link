package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class MockedClockContextTest {

  @MockitoBean private Clock clock;
  @Autowired private ApplicationContext context;

  @Test
  void theApplicationStartsWithAMockedClock() {
    assertThat(context.getBean(Clock.class)).isSameAs(clock);
    assertThat(context.getBean(RelayTally.class)).isNotNull();
  }
}
