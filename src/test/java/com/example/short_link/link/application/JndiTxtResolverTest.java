package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JndiTxtResolverTest {

  private final JndiTxtResolver resolver = new JndiTxtResolver();

  @Test
  void returnsEmptyListForInvalidHost() {
    // 일부러 절대 resolve 되지 않을 host — NamingException 캐치 분기
    List<String> result = resolver.lookup("definitely-not-a-real-host-12345.invalid.");
    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyListForEmptyHost() {
    List<String> result = resolver.lookup("");
    assertThat(result).isEmpty();
  }
}
