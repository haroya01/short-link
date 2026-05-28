package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

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

  @ArchTest
  static final ArchRule domainDoesNotDependOnApplication =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..");

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

  // ─── Architecture Boundaries ─────────────────────────────────────────

  @ArchTest
  static final ArchRule apacheHttpClientConfined =
      noClasses()
          .that()
          .resideOutsideOfPackages("..common.net..", "..common.config..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.apache.hc..");

  @ArchTest
  static final ArchRule springDataNotInApplication =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.data..");

  @ArchTest
  static final ArchRule springDataNotInDomain =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.data..");

  @ArchTest
  static final ArchRule contextsAreFreeOfCycles =
      slices().matching("com.example.short_link.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule queryServicesLiveInApplicationRead =
      classes()
          .that()
          .haveSimpleNameEndingWith("QueryService")
          .should()
          .resideInAPackage("..application.read..");

  @ArchTest
  static final ArchRule controllersLiveInPresentation =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAPackage("..presentation..");

  // 트랜잭션 경계는 application / infrastructure 만 갖는다. presentation 에 @Transactional 이
  // 박히면 컨트롤러가 도메인 트랜잭션을 들고 가는 형태가 되고, domain 에 박히면 entity 가
  // cross-cutting 책임을 떠안아 도메인 모델 순수성이 깨진다.
  @ArchTest
  static final ArchRule transactionalNotInPresentationOrDomain =
      methods()
          .that()
          .areAnnotatedWith(Transactional.class)
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
