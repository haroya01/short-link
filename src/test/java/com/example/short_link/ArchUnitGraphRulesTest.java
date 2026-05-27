package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(
    packages = "com.example.short_link",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchUnitGraphRulesTest {

  // ─── Layer direction (strict) ──────────────────────────────────────────

  @ArchTest
  static final ArchRule domainDoesNotDependOnPresentation =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..presentation..");

  // link.domain.LinkWebhookEntity 가 link.application.helper.WebhookFormat 의존 (9 위반).
  // WebhookFormat enum 이 application 에 있는 게 root cause. 별도 PR 로 domain 이동 예정.
  @ArchTest
  static final ArchRule domainDoesNotDependOnApplication =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..application.."));

  @ArchTest
  static final ArchRule applicationDoesNotDependOnPresentation =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..presentation..");

  // ─── External SDK isolation (strict) ──────────────────────────────────

  @ArchTest
  static final ArchRule stripeSdkConfinedToBillingInfrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("..billing.infrastructure.stripe..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.stripe..");

  @ArchTest
  static final ArchRule awsSdkConfinedToCommonStorage =
      noClasses()
          .that()
          .resideOutsideOfPackage("..common.storage.s3..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("software.amazon.awssdk..");

  @ArchTest
  static final ArchRule maxmindSdkConfinedToCommonGeoip =
      noClasses()
          .that()
          .resideOutsideOfPackages("..common.geoip..", "..common.config..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.maxmind..");

  // ─── Naming convention (strict) ───────────────────────────────────────

  @ArchTest
  static final ArchRule useCasesLiveInApplicationWrite =
      classes()
          .that()
          .haveSimpleNameEndingWith("UseCase")
          .should()
          .resideInAPackage("..application.write..");

  // ─── Frozen (baseline 동결, 신규 위반만 fail) ──────────────────────────

  // Apache HttpClient 5 — common.net 외에 link.application.OgScraper,
  // link.scheduler.LinkWebhookDispatcher 가 직접 사용 중. 신규 직접 사용 차단.
  @ArchTest
  static final ArchRule apacheHttpClientConfinedExceptKnownLeaks =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideOutsideOfPackages("..common.net..", "..common.config..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("org.apache.hc.."));

  // Spring Data 타입 (Page, Pageable, JpaRepository 등) 이 application 에 누출.
  // D2 (repository port/adapter) 가 phase 4 skip 결정으로 미정. 신규 누출만 차단.
  @ArchTest
  static final ArchRule springDataNotInApplication =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("..application..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("org.springframework.data.."));

  // Context 간 양방향 의존 (cycle). 현재 6+ cycle 동결. 신규 cycle 차단.
  @ArchTest
  static final ArchRule contextsAreFreeOfCycles =
      FreezingArchRule.freeze(
          slices().matching("com.example.short_link.(*)..").should().beFreeOfCycles());

  // *QueryService 는 application.read 안에 — 현재 user/application/UserQueryService
  // 1건 예외. 신규 위반 차단.
  @ArchTest
  static final ArchRule queryServicesLiveInApplicationRead =
      FreezingArchRule.freeze(
          classes()
              .that()
              .haveSimpleNameEndingWith("QueryService")
              .should()
              .resideInAPackage("..application.read.."));

  // *Controller 는 presentation 안에 — 현재 common/pow, common/web 예외.
  @ArchTest
  static final ArchRule controllersLiveInPresentation =
      FreezingArchRule.freeze(
          classes()
              .that()
              .haveSimpleNameEndingWith("Controller")
              .should()
              .resideInAPackage("..presentation.."));

  // 트랜잭션 경계는 application / infrastructure 만 갖는다. presentation 에 @Transactional 이
  // 박히면 컨트롤러가 도메인 트랜잭션을 들고 가는 형태가 되고, domain 에 박히면 entity 가
  // cross-cutting 책임을 떠안아 도메인 모델 순수성이 깨진다.
  @ArchTest
  static final ArchRule transactionalNotInPresentationOrDomain =
      methods()
          .that()
          .areAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
          .should()
          .beDeclaredInClassesThat()
          .resideOutsideOfPackages("..presentation..", "..domain..");

  // Properties 는 immutable record + compact constructor — Phase 0 (PR #319) 컨벤션. class
  // 로 신설되면 setter / mutable field 누설 위험이 생긴다.
  @ArchTest
  static final ArchRule propertiesAreRecords =
      classes()
          .that()
          .haveSimpleNameEndingWith("Properties")
          .and()
          .resideInAPackage("com.example.short_link..")
          .should()
          .beRecords();
}
