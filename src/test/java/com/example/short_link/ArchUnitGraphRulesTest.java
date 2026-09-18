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
  static final ArchRule domainDoesNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnPresentation =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..presentation..");

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

  @ArchTest
  static final ArchRule jjwtSdkConfinedToUserApplication =
      noClasses()
          .that()
          .resideOutsideOfPackage("..user.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.jsonwebtoken..");

  @ArchTest
  static final ArchRule jsoupSdkConfinedToOgScraper =
      noClasses()
          .that()
          .resideOutsideOfPackage("..link.og.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.jsoup..");

  @ArchTest
  static final ArchRule burstHeuristicConfinedToBotClassifier =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(
              "com.example.short_link.link.classifier.application.BotClassifier")
          .should()
          .callMethod(
              "com.example.short_link.link.classifier.application.BotHeuristic",
              "isSuspectBurst",
              "java.lang.String");

  @ArchTest
  static final ArchRule yauaaSdkConfinedToUserAgentClassifier =
      noClasses()
          .that()
          .resideOutsideOfPackages("..common.config..", "..link.classifier.application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("nl.basjes..");

  @ArchTest
  static final ArchRule zxingSdkConfinedToQrPngEncoder =
      noClasses()
          .that()
          .resideOutsideOfPackage("..campaign.application.helper..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.google.zxing..");

  @ArchTest
  static final ArchRule useCasesLiveInApplicationWrite =
      classes()
          .that()
          .haveSimpleNameEndingWith("UseCase")
          .should()
          .resideInAPackage("..application.write..");

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

  // 트랜잭션은 application과 infrastructure에서만 관리한다.
  @ArchTest
  static final ArchRule transactionalNotInPresentationOrDomain =
      methods()
          .that()
          .areAnnotatedWith(Transactional.class)
          .should()
          .beDeclaredInClassesThat()
          .resideOutsideOfPackages("..presentation..", "..domain..");

  // 설정은 불변 record로 유지한다.
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
