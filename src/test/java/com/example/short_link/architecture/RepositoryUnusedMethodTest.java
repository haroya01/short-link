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

/**
 * Catches the kind of dead Repository method that grew on {@code
 * ProfileVisitRepository.countByProfileUserIdAndVisitedAtAfter} — declared but never called from
 * anywhere in main source. Spring Data JPA derivation queries are still static call targets, so
 * byte-code analysis picks up real uses; methods with zero callers are confirmed dead.
 *
 * <p>Scope: only our project's {@code com.example.short_link.*.domain.**Repository} interfaces.
 * Inherited methods from Spring's JpaRepository / CrudRepository (save / findById / etc.) are
 * skipped because they're declared in framework code, not ours. Test-source callers are ignored on
 * purpose — a method "only used in tests" is still dead from the production perspective.
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

  private static ArchCondition<JavaMethod> beCalledFromOutsideOwner() {
    return new ArchCondition<>("be called from outside the declaring repository") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        boolean calledExternally =
            method.getAccessesToSelf().stream()
                .anyMatch(access -> !access.getOriginOwner().equals(method.getOwner()));
        if (!calledExternally) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  "Repository method " + method.getFullName() + " has zero callers in main src"));
        }
      }
    };
  }
}
