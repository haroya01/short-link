package com.example.short_link.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Catches dead methods on Repository / Service / UseCase classes. Byte-code call analysis sees
 * every static call site (including Spring Data JPA derivation queries and AOP'd targets) — a
 * method with zero callers in main source is confirmed dead, even when grep would have missed it
 * (substring match / declaring-class noise).
 *
 * <p>Excluded on purpose:
 *
 * <ul>
 *   <li>Test-source callers — a method "only used in tests" is still dead from production.
 *   <li>Controllers — Spring routes HTTP requests via {@code @RequestMapping}, so there's no
 *       byte-code caller.
 *   <li>Schedulers / event listeners — {@code @Scheduled}, {@code @EventListener},
 *       {@code @TransactionalEventListener} are reflection-invoked by Spring.
 * </ul>
 */
@AnalyzeClasses(
    packages = "com.example.short_link",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class RepositoryUnusedMethodTest {

  @ArchTest
  static final ArchRule no_unused_repository_methods =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.example.short_link..")
          .and()
          .arePublic()
          .and()
          .areNotStatic()
          .should(beCalledFromOutsideOwner());

  @ArchTest
  static final ArchRule no_unused_service_or_usecase_methods =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Service")
          .or()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("UseCase")
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.example.short_link..")
          .and()
          .arePublic()
          .and()
          .areNotStatic()
          .and()
          .areNotAnnotatedWith(Scheduled.class)
          .and()
          .areNotAnnotatedWith(EventListener.class)
          .and()
          .areNotAnnotatedWith(TransactionalEventListener.class)
          .and()
          .areNotAnnotatedWith(Async.class)
          // EmailLeadService.submit(4-arg) is an internal helper exposed for the existing extended
          // test suite. Production goes through submitPublic(3-arg). Narrowing to private is a
          // separate cleanup once those tests migrate to the public entry point.
          .and()
          .doNotHaveFullName(
              "com.example.short_link.profile.application.email.EmailLeadService.submit("
                  + "java.lang.Long, java.lang.Long, java.lang.String, java.lang.String)")
          .should(beCalledFromOutsideOwner());

  private static ArchCondition<JavaMethod> beCalledFromOutsideOwner() {
    return new ArchCondition<>("be called from outside its declaring class") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean calledExternally =
            method.getAccessesToSelf().stream()
                .anyMatch(access -> !access.getOriginOwner().equals(method.getOwner()));
        if (!calledExternally) {
          events.add(
              SimpleConditionEvent.violated(
                  method, "Method " + method.getFullName() + " has zero callers in main src"));
        }
      }
    };
  }
}
