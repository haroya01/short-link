package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-06-23 prod 커넥션 누수 인시던트 회귀 가드. leak 스택이 가리킨 진범은 open-session-in-view 였다 — {@code GET
 * /api/v1/links/{code}/stream} SSE 요청(최대 5분)이 lookup 에서 잡은 JDBC 커넥션을 OSIV 가 요청 끝까지 스레드에 묶어, 열린
 * 스트림마다 풀 커넥션 하나를 5분씩 점유했다.
 *
 * <p>그 메커니즘의 실체는 {@link OpenEntityManagerInViewInterceptor} 다 — open-in-view 가 켜져 있을 때만 Spring Boot
 * 가 이 빈을 등록하고, 이 인터셉터가 EntityManager(=커넥션)를 요청 수명에 바인딩한다. 이 빈이 컨텍스트에 없다는 것은 곧 어떤 요청도(긴 SSE async
 * 포함) 커넥션을 요청 수명 동안 쥐지 않는다는 뜻이다. 누가 실수로 open-in-view 를 되살리면 이 빈이 다시 등장하고 이 테스트가 깨진다.
 */
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
