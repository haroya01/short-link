package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the test asks whether a transaction is open while the remote server is asked.
@SpringBootTest(properties = "short-link.federation.relay.enabled=true")
@ActiveProfiles("test")
class RemoteLookupsOutsideTransactionIntegrationTest {

  @MockitoBean private FederationHttp http;
  @Autowired private RemoteFollowing following;
  @Autowired private RelaySubscriptions relays;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private String host;
  private String actor;
  private Long userId;

  @BeforeEach
  void aServerThatAnswersForAlice() {
    host = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + ".example";
    actor = "https://" + host + "/users/alice";
    String jrd =
        """
        {"subject": "acct:alice@%s",
         "links": [{"rel": "self", "type": "application/activity+json", "href": "%s"}]}
        """
            .formatted(host, actor);
    when(http.webFinger(any(), any(), any()))
        .thenAnswer(
            inv -> {
              calls.add("webfinger " + where());
              return new FederationHttp.Result.Ok(
                  200, jrd.getBytes(StandardCharsets.UTF_8), "application/jrd+json");
            });
    when(http.get(eq(URI.create(actor)), any()))
        .thenAnswer(
            inv -> {
              calls.add("fetch " + where());
              return new FederationHttp.Result.Ok(
                  200,
                  RemoteActorFixtures.MASTODON
                      .replace("mastodon.example", host)
                      .getBytes(StandardCharsets.UTF_8),
                  "application/activity+json");
            });

    String name = "fed" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    userId = users.save(user).getId();
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void forgetTheServer() {
    jdbc.update("DELETE FROM federation_delivery WHERE inbox_host = ?", host);
    jdbc.update("DELETE FROM federation_relay WHERE actor_uri = ?", actor);
    jdbc.update("DELETE FROM federation_remote_actor WHERE actor_uri = ?", actor);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private long stored(String sql) {
    return jdbc.queryForObject(sql, Long.class, actor);
  }

  @Test
  void lookingUpAnAccountOnAnotherServerAsksItOutsideATransaction() {
    following.lookup(userId, "alice@" + host);

    assertThat(calls).containsExactly("webfinger outside", "fetch outside");
    assertThat(stored("SELECT COUNT(*) FROM federation_remote_actor WHERE actor_uri = ?"))
        .isEqualTo(1);
  }

  @Test
  void subscribingToARelayAsksItOutsideTheTransactionThatStoresTheFollow() {
    relays.subscribe(actor);

    assertThat(calls).containsExactly("fetch outside");
    assertThat(stored("SELECT COUNT(*) FROM federation_relay WHERE actor_uri = ?")).isEqualTo(1);
  }
}
