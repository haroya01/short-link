package com.example.short_link.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.application.write.MobileExchangeCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisMobileExchangeCodeStoreTest {

  @Autowired private MobileExchangeCodeStore store;
  @Autowired private StringRedisTemplate redis;

  @Test
  void createdCodeConsumesExactlyOnce() {
    String code = store.create(42L);

    assertThat(store.consume(code)).contains(42L);
    assertThat(store.consume(code)).isEmpty();
  }

  @Test
  void unknownCodeConsumesToEmpty() {
    assertThat(store.consume("never-issued")).isEmpty();
  }

  @Test
  void corruptValueConsumesToEmptyInsteadOfThrowing() {
    redis.opsForValue().set("mobile-exchange:corrupt", "not-a-user-id");

    assertThat(store.consume("corrupt")).isEmpty();
  }
}
