package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;

// open-in-view가 켜지면 이 인터셉터가 등록되고, SSE처럼 긴 요청이 커넥션을 요청 끝까지 붙잡아 풀을 잠식한다.
// 누가 open-in-view를 되살리면 이 테스트가 깨진다.
@SpringBootTest
@ActiveProfiles("test")
class OpenSessionInViewDisabledTest {

  @Autowired private ApplicationContext ctx;

  @Test
  void openInViewIsExplicitlyDisabled() {
    assertThat(ctx.getEnvironment().getProperty("spring.jpa.open-in-view"))
        .as("spring.jpa.open-in-view must stay false (SSE connection-leak guard)")
        .isEqualTo("false");
  }

  @Test
  void osivInterceptorIsNotRegistered() {
    assertThat(ctx.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class))
        .as(
            "OpenEntityManagerInViewInterceptor must not exist — it is what pinned the /stream "
                + "connection for the request lifetime")
        .isEmpty();
  }
}
