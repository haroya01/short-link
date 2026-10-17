package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.repository.RemoteDomainReader;
import com.example.short_link.federation.domain.repository.RemoteDomainReader.KnownDomain;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RemoteDomainReaderIntegrationTest {

  @Autowired private RemoteDomainReader reader;
  @Autowired private EntityManager em;

  private final String tag = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

  private String domain(String name) {
    return name + "-" + tag + ".example";
  }

  private void actors(String domain, int count) {
    for (int i = 0; i < count; i++) {
      String uri = "https://" + domain + "/users/u" + i;
      em.createNativeQuery(
              "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
                  + " username, domain, fetched_at, created_at, updated_at)"
                  + " VALUES (?1, ?2, 'pem', ?3, ?4, ?5, NOW(6), NOW(6), NOW(6))")
          .setParameter(1, uri)
          .setParameter(2, uri + "#main-key")
          .setParameter(3, uri + "/inbox")
          .setParameter(4, "u" + i)
          .setParameter(5, domain)
          .executeUpdate();
    }
  }

  private void block(String domain, String severity) {
    em.createNativeQuery(
            "INSERT INTO federation_domain_block (domain, severity, created_at)"
                + " VALUES (?1, ?2, NOW(6))")
        .setParameter(1, domain)
        .setParameter(2, severity)
        .executeUpdate();
  }

  private List<KnownDomain> ours(List<KnownDomain> known) {
    return known.stream().filter(found -> found.domain().contains(tag)).toList();
  }

  @Test
  void eachServerOnceWithItsKnownAccountsAndNoneThisServerBlocked() {
    actors(domain("many"), 3);
    actors(domain("one"), 1);
    actors(domain("gone"), 2);
    actors(domain("quiet"), 2);
    actors("social." + domain("parent"), 1);
    block(domain("gone"), "SUSPEND");
    block(domain("quiet"), "LIMIT");
    block(domain("parent"), "SUSPEND");
    block(domain("namedgone"), "LIMIT");

    List<KnownDomain> known =
        reader.known(
            List.of(domain("named"), domain("one"), domain("namedgone")), Integer.MAX_VALUE);

    assertThat(ours(known))
        .containsExactlyInAnyOrder(
            new KnownDomain(domain("many"), 3),
            new KnownDomain(domain("one"), 1),
            new KnownDomain(domain("named"), 0));
    assertThat(ours(reader.known(List.of(), Integer.MAX_VALUE)))
        .containsExactlyInAnyOrder(
            new KnownDomain(domain("many"), 3), new KnownDomain(domain("one"), 1));
  }

  @Test
  void theCapCountsKnownServersAndNeverDropsANamedOne() {
    actors(domain("many"), 3);

    assertThat(reader.known(List.of(), 1)).hasSizeLessThanOrEqualTo(1);
    assertThat(reader.known(List.of(domain("named")), 1))
        .hasSizeLessThanOrEqualTo(2)
        .contains(new KnownDomain(domain("named"), 0));
  }
}
