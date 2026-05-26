package com.example.short_link.customdomain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.customdomain.application.write.AutoVerifyCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.CustomDomainOwnership;
import com.example.short_link.customdomain.application.write.DeleteCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.RegisterCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.VerifyCustomDomainUseCase;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.customdomain.exception.CustomDomainException;
import com.example.short_link.link.classifier.application.helper.TxtResolver;
import com.example.short_link.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomDomainServiceTest {

  @Mock private CustomDomainRepository repository;

  private SimpleMeterRegistry meterRegistry;
  private StubTxtResolver txtResolver;
  private CustomDomainQueryService queryService;
  private RegisterCustomDomainUseCase register;
  private VerifyCustomDomainUseCase verify;
  private AutoVerifyCustomDomainUseCase autoVerify;
  private DeleteCustomDomainUseCase delete;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    txtResolver = new StubTxtResolver();
    queryService = new CustomDomainQueryService(repository);
    register = new RegisterCustomDomainUseCase(repository, meterRegistry);
    CustomDomainOwnership ownership = new CustomDomainOwnership(repository);
    verify = new VerifyCustomDomainUseCase(ownership, meterRegistry, txtResolver);
    autoVerify = new AutoVerifyCustomDomainUseCase(repository, meterRegistry, txtResolver);
    delete = new DeleteCustomDomainUseCase(ownership, repository, meterRegistry);
  }

  private CustomDomainEntity domain(long id, long userId, String name, boolean verified) {
    CustomDomainEntity d = new CustomDomainEntity(userId, name, "kurl-verify=abc");
    TestEntities.withId(d, id);
    TestEntities.setField(d, "createdAt", Instant.now());
    if (verified) d.markVerified();
    return d;
  }

  @Test
  void listMapsToSummaryShape() {
    when(repository.findAllByUserIdOrderByIdAsc(7L))
        .thenReturn(List.of(domain(1L, 7L, "go.example.com", true)));
    List<DomainSummary> out = queryService.list(7L);
    assertThat(out).hasSize(1);
    assertThat(out.get(0).domain()).isEqualTo("go.example.com");
    assertThat(out.get(0).verificationHost()).isEqualTo("_kurl-verify.go.example.com");
    assertThat(out.get(0).verified()).isTrue();
  }

  @Test
  void registerRejectsBadFormat() {
    assertThatThrownBy(() -> register.execute(7L, "")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> register.execute(7L, "no-tld"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> register.execute(7L, "a".repeat(300)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registerRejectsKurlReservedDomains() {
    assertThatThrownBy(() -> register.execute(7L, "kurl.me"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> register.execute(7L, "go.kurl.me"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registerRejectsDuplicate() {
    when(repository.existsByDomain("go.example.com")).thenReturn(true);
    assertThatThrownBy(() -> register.execute(7L, "go.example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already registered");
  }

  @Test
  void registerRejectsOverPerUserCap() {
    when(repository.existsByDomain(anyString())).thenReturn(false);
    List<CustomDomainEntity> five = new ArrayList<>();
    for (int i = 0; i < CustomDomainPolicy.MAX_PER_USER; i++) {
      five.add(domain(i + 1L, 7L, "a" + i + ".example.com", false));
    }
    when(repository.findAllByUserIdOrderByIdAsc(7L)).thenReturn(five);
    assertThatThrownBy(() -> register.execute(7L, "go.example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max");
  }

  @Test
  void registerNormalizesAndSaves() {
    when(repository.existsByDomain("go.example.com")).thenReturn(false);
    when(repository.findAllByUserIdOrderByIdAsc(7L)).thenReturn(List.of());
    when(repository.save(any(CustomDomainEntity.class)))
        .thenAnswer(
            inv -> {
              CustomDomainEntity saved = inv.getArgument(0);
              TestEntities.setField(saved, "createdAt", Instant.now());
              return saved;
            });
    DomainSummary out = register.execute(7L, "  HTTPS://go.example.com/path/x  ");
    assertThat(out.domain()).isEqualTo("go.example.com");
    assertThat(out.verificationToken()).startsWith("kurl-verify=");
    assertThat(meterRegistry.counter("custom_domain.registered").count()).isEqualTo(1.0);
  }

  @Test
  void verifyOwnerCheckIsEnforced() {
    CustomDomainEntity d = domain(1L, 99L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    assertThatThrownBy(() -> verify.execute(7L, 1L)).isInstanceOf(CustomDomainException.class);
  }

  @Test
  void verifyMissingDomainThrows() {
    when(repository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> verify.execute(7L, 1L)).isInstanceOf(CustomDomainException.class);
  }

  @Test
  void verifyFailureMarksAndCounts() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    assertThatThrownBy(() -> verify.execute(7L, 1L)).isInstanceOf(CustomDomainException.class);
    assertThat(meterRegistry.counter("custom_domain.verify", "result", "failed").count())
        .isEqualTo(1.0);
    assertThat(d.getLastCheckedAt()).isNotNull();
  }

  @Test
  void verifySuccessMarksAndCounts() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    txtResolver.put("_kurl-verify.go.example.com", "kurl-verify=abc");
    DomainSummary out = verify.execute(7L, 1L);
    assertThat(out.verified()).isTrue();
    assertThat(d.isVerified()).isTrue();
    assertThat(meterRegistry.counter("custom_domain.verify", "result", "ok").count())
        .isEqualTo(1.0);
  }

  @Test
  void autoVerifyOneFailureUpdatesCheckTimestamp() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    boolean out = autoVerify.execute(d);
    assertThat(out).isFalse();
    assertThat(d.isVerified()).isFalse();
    assertThat(d.getLastCheckedAt()).isNotNull();
  }

  @Test
  void autoVerifyOneSuccessMarksVerified() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    txtResolver.put("_kurl-verify.go.example.com", "kurl-verify=abc");
    boolean out = autoVerify.execute(d);
    assertThat(out).isTrue();
    assertThat(d.isVerified()).isTrue();
    assertThat(meterRegistry.counter("custom_domain.verify", "result", "auto_ok").count())
        .isEqualTo(1.0);
  }

  @Test
  void autoVerifyOneGoneEntityReturnsFalse() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findById(1L)).thenReturn(Optional.empty());
    boolean out = autoVerify.execute(d);
    assertThat(out).isFalse();
  }

  @Test
  void findPendingWithinWindowDelegates() {
    queryService.findPendingWithinWindow();
    verify(repository).findAllByVerifiedFalseAndCreatedAtAfter(any(Instant.class));
  }

  @Test
  void deleteOwnerDelegatesAndCounts() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", true);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    delete.execute(7L, 1L);
    verify(repository).delete(d);
    assertThat(meterRegistry.counter("custom_domain.deleted").count()).isEqualTo(1.0);
  }

  @Test
  void deleteNonOwnerThrows() {
    CustomDomainEntity d = domain(1L, 99L, "go.example.com", true);
    when(repository.findById(1L)).thenReturn(Optional.of(d));
    assertThatThrownBy(() -> delete.execute(7L, 1L)).isInstanceOf(CustomDomainException.class);
    verify(repository, never()).delete(any());
  }

  @Test
  void resolveOwnerReturnsNullForMissingOrBlank() {
    assertThat(queryService.resolveOwner(null)).isNull();
    assertThat(queryService.resolveOwner("   ")).isNull();
    when(repository.findByDomain("unknown.example.com")).thenReturn(Optional.empty());
    assertThat(queryService.resolveOwner("unknown.example.com")).isNull();
  }

  @Test
  void resolveOwnerSkipsUnverified() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", false);
    when(repository.findByDomain("go.example.com")).thenReturn(Optional.of(d));
    assertThat(queryService.resolveOwner("go.example.com")).isNull();
  }

  @Test
  void resolveOwnerReturnsUserIdForVerified() {
    CustomDomainEntity d = domain(1L, 7L, "go.example.com", true);
    when(repository.findByDomain("go.example.com")).thenReturn(Optional.of(d));
    assertThat(queryService.resolveOwner("go.example.com")).isEqualTo(7L);
  }

  private static final class StubTxtResolver implements TxtResolver {
    private final Map<String, List<String>> records = new HashMap<>();

    void put(String host, String value) {
      records.computeIfAbsent(host, k -> new ArrayList<>()).add(value);
    }

    @Override
    public List<String> lookup(String host) {
      return records.getOrDefault(host, List.of());
    }
  }
}
