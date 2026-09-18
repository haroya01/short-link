package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ArchUnitSemanticRulesFixtureTest {

  @Test
  void selfInvocationIsFlaggedOnlyWhereItChangesBehaviour() {
    List<String> violations =
        violations(
            ArchUnitSemanticRulesTest.proxiedBehaviourIsNotBypassedBySelfInvocation,
            RequiresNewCalledFromItsOwnClass.class,
            TransactionalCalledFromPlainMethod.class,
            TransactionalCalledFromTransactionalMethod.class);

    assertThat(violations)
        .hasSize(2)
        .anyMatch(v -> v.contains("RequiresNewCalledFromItsOwnClass"))
        .anyMatch(v -> v.contains("TransactionalCalledFromPlainMethod"))
        .noneMatch(v -> v.contains("TransactionalCalledFromTransactionalMethod"));
  }

  @Test
  void outboundCallsAreFlaggedOnlyInsideTransactions() {
    List<String> violations =
        violations(
            ArchUnitSemanticRulesTest.transactionsDoNotCallOutboundClients,
            CallsNetworkInsideTransaction.class,
            CallsNetworkOutsideTransaction.class);

    assertThat(violations).hasSize(1).allMatch(v -> v.contains("CallsNetworkInsideTransaction"));
  }

  @Test
  void bothWaysOfEvictingWithoutWaitingAreFlagged() {
    assertThat(
            violations(
                ArchUnitSemanticRulesTest.cacheEvictAnnotationIsNotUsed, EvictsByAnnotation.class))
        .hasSize(1);
    assertThat(
            violations(
                ArchUnitSemanticRulesTest.cacheEntriesAreNotEvictedWithoutWaiting,
                EvictsDirectly.class))
        .hasSize(1);
  }

  private static List<String> violations(ArchRule rule, Class<?>... fixtures) {
    JavaClasses classes = new ClassFileImporter().importClasses(fixtures);
    return rule.evaluate(classes).getFailureReport().getDetails();
  }

  static class RequiresNewCalledFromItsOwnClass {
    @Transactional
    public void outer() {
      inner();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {}
  }

  static class TransactionalCalledFromPlainMethod {
    public void outer() {
      inner();
    }

    @Transactional
    public void inner() {}
  }

  static class TransactionalCalledFromTransactionalMethod {
    @Transactional
    public void outer() {
      inner();
    }

    @Transactional(readOnly = true)
    public void inner() {}
  }

  static class CallsNetworkInsideTransaction {
    private final HttpClient client = HttpClient.newHttpClient();

    @Transactional
    public Object run() {
      return client.version();
    }
  }

  static class CallsNetworkOutsideTransaction {
    private final HttpClient client = HttpClient.newHttpClient();

    public Object run() {
      return client.version();
    }
  }

  static class EvictsByAnnotation {
    @CacheEvict("link")
    public void run() {}
  }

  static class EvictsDirectly {
    public void run(Cache cache) {
      cache.evict("key");
    }
  }
}
