package com.example.short_link.customdomain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.example.short_link.common.net.TxtResolver;
import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.write.AutoVerifyCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.RegisterCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.VerifyCustomDomainUseCase;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.customdomain.exception.CustomDomainErrorCode;
import com.example.short_link.customdomain.exception.CustomDomainException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(VerifyCustomDomainTransactionTest.DnsConfig.class)
class VerifyCustomDomainTransactionTest {

  @Autowired private RegisterCustomDomainUseCase register;
  @Autowired private VerifyCustomDomainUseCase verify;
  @Autowired private AutoVerifyCustomDomainUseCase autoVerify;
  @MockitoSpyBean private CustomDomainRepository domains;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private StubTxtResolver resolver;

  private Long userId;
  private Long domainId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    userId = users.save(new UserEntity(suffix + "@example.com", "google", suffix)).getId();
    DomainSummary registered = register.execute(userId, "verify-" + suffix + ".example.com");
    domainId = registered.id();
    resolver.values = List.of();
    resolver.transactionActiveDuringLookup = null;
    resolver.lookupTransactions.clear();
  }

  @AfterEach
  void cleanUp() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              if (domainId != null) domains.findById(domainId).ifPresent(domains::delete);
              if (userId != null) users.deleteById(userId);
            });
  }

  @Test
  void failedDnsCheckCommitsItsTimestampBeforeReportingTheError() {
    assertThatThrownBy(() -> verify.execute(userId, domainId))
        .isInstanceOfSatisfying(
            CustomDomainException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED));

    CustomDomainEntity reloaded = domains.findById(domainId).orElseThrow();
    assertThat(reloaded.isVerified()).isFalse();
    assertThat(reloaded.getVerifiedAt()).isNull();
    assertThat(reloaded.getLastCheckedAt()).isNotNull();
    assertThat(resolver.transactionActiveDuringLookup).isFalse();
  }

  @Test
  void failedCallerTransactionDoesNotRollBackTheDnsCheckHistory() {
    String rolledBackEmail = "rolled-back-" + UUID.randomUUID() + "@example.com";

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                              .isTrue();
                          users.save(new UserEntity(rolledBackEmail, "google", rolledBackEmail));
                          verify.execute(userId, domainId);
                        }))
        .isInstanceOfSatisfying(
            CustomDomainException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED));

    assertThat(users.findByEmail(rolledBackEmail)).isEmpty();
    CustomDomainEntity reloaded = domains.findById(domainId).orElseThrow();
    assertThat(reloaded.isVerified()).isFalse();
    assertThat(reloaded.getLastCheckedAt()).isNotNull();
    assertThat(resolver.transactionActiveDuringLookup).isFalse();
  }

  @Test
  void successfulDnsCheckReturnsAndPersistsTheSameVerificationResult() {
    resolver.values = List.of(domains.findById(domainId).orElseThrow().getVerificationToken());

    DomainSummary result = verify.execute(userId, domainId);

    CustomDomainEntity reloaded = domains.findById(domainId).orElseThrow();
    assertThat(result.verified()).isTrue();
    assertThat(reloaded.isVerified()).isTrue();
    assertThat(reloaded.getVerifiedAt()).isNotNull();
    assertThat(reloaded.getLastCheckedAt()).isEqualTo(reloaded.getVerifiedAt());
    assertThat(resolver.transactionActiveDuringLookup).isFalse();
  }

  @Test
  void automaticDnsCheckRunsOutsideTheCallerTransactionAndCommitsItsOutcome() {
    CustomDomainEntity pending = domains.findById(domainId).orElseThrow();
    resolver.values = List.of(pending.getVerificationToken());

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(autoVerify.execute(pending)).isTrue();
              status.setRollbackOnly();
            });

    CustomDomainEntity reloaded = domains.findById(domainId).orElseThrow();
    assertThat(reloaded.isVerified()).isTrue();
    assertThat(reloaded.getVerifiedAt()).isNotNull();
    assertThat(resolver.transactionActiveDuringLookup).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, true", "true, true"})
  void manualAndAutomaticChecksSerializeTheirOutcomesAndKeepTheFirstVerificationTime(
      boolean manualSuccess, boolean automaticSuccess) throws Exception {
    CustomDomainEntity pending = domains.findById(domainId).orElseThrow();
    String token = pending.getVerificationToken();
    resolver.values = manualSuccess ? List.of(token) : List.of();
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch secondAttempted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstUpdate = new AtomicBoolean(true);
    AtomicReference<Instant> firstVerificationSeenBySecond = new AtomicReference<>();

    doAnswer(
            invocation -> {
              boolean first = firstUpdate.getAndSet(false);
              if (!first) secondAttempted.countDown();
              @SuppressWarnings("unchecked")
              Optional<CustomDomainEntity> current =
                  (Optional<CustomDomainEntity>) invocation.callRealMethod();
              if (first) {
                firstLocked.countDown();
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
              } else {
                firstVerificationSeenBySecond.set(current.orElseThrow().getVerifiedAt());
              }
              return current;
            })
        .when(domains)
        .findByIdForUpdate(domainId);

    try (var workers = Executors.newFixedThreadPool(2)) {
      var manual =
          workers.submit(
              () -> {
                if (manualSuccess) return verify.execute(userId, domainId);
                assertThatThrownBy(() -> verify.execute(userId, domainId))
                    .isInstanceOfSatisfying(
                        CustomDomainException.class,
                        e ->
                            assertThat(e.errorCode())
                                .isEqualTo(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED));
                return null;
              });
      try {
        assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
        resolver.values = automaticSuccess ? List.of(token) : List.of();
        var automatic = workers.submit(() -> autoVerify.execute(pending));
        assertThat(secondAttempted.await(10, TimeUnit.SECONDS)).isTrue();

        // The second writer must wait for the first transaction, not read its previous state.
        assertThatThrownBy(() -> automatic.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        releaseFirst.countDown();

        DomainSummary manualResult = manual.get(10, TimeUnit.SECONDS);
        assertThat(automatic.get(10, TimeUnit.SECONDS)).isEqualTo(automaticSuccess);
        if (manualSuccess) assertThat(manualResult.verified()).isTrue();
      } finally {
        releaseFirst.countDown();
      }
    }

    CustomDomainEntity reloaded = domains.findById(domainId).orElseThrow();
    assertThat(reloaded.isVerified()).isTrue();
    assertThat(reloaded.getVerifiedAt()).isNotNull();
    assertThat(reloaded.getLastCheckedAt()).isNotNull();
    if (manualSuccess) {
      assertThat(firstVerificationSeenBySecond.get()).isNotNull();
      assertThat(reloaded.getVerifiedAt()).isEqualTo(firstVerificationSeenBySecond.get());
    }
    assertThat(resolver.lookupTransactions).containsExactly(false, false);
  }

  @TestConfiguration
  static class DnsConfig {
    @Bean
    @Primary
    StubTxtResolver transactionTestTxtResolver() {
      return new StubTxtResolver();
    }
  }

  static class StubTxtResolver implements TxtResolver {
    private volatile List<String> values = List.of();
    private volatile Boolean transactionActiveDuringLookup;
    private final ConcurrentLinkedQueue<Boolean> lookupTransactions = new ConcurrentLinkedQueue<>();

    @Override
    public List<String> lookup(String name) {
      transactionActiveDuringLookup = TransactionSynchronizationManager.isActualTransactionActive();
      lookupTransactions.add(transactionActiveDuringLookup);
      return values;
    }
  }
}
