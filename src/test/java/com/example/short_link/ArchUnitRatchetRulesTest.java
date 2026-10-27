package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.persistence.Entity;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Frozen rules keep today's violations in src/test/resources/archunit_store and fail on new ones;
// the ceiling pins how many there are, so the count only comes down.
@AnalyzeClasses(
    packages = "com.example.short_link",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchUnitRatchetRulesTest {

  static final Map<String, Integer> CEILINGS =
      Map.of(
          "moduleInternalsStayInTheirModule", 81,
          "theSystemClockIsReadInConfigAlone", 0);

  private static final List<String> MODULE_ROOTS =
      List.of("com.example.short_link.", "archfixtures.modules.");

  private static final Pattern INTERNAL =
      Pattern.compile("\\.(infrastructure|domain\\.repository)(\\.|$)");

  private static final Set<String> SYSTEM_CLOCKS =
      Set.of("systemUTC", "systemDefaultZone", "system");

  static final ArchRule moduleInternalsStayInTheirModule =
      classes()
          .should(reachAnotherModuleOnlyThroughItsApplicationOrDomainValues())
          .as("moduleInternalsStayInTheirModule");

  static final ArchRule theSystemClockIsReadInConfigAlone =
      classes()
          .that()
          .resideOutsideOfPackage("..config..")
          .should(takeTheClockBeanInsteadOfTheSystemClock())
          .as("theSystemClockIsReadInConfigAlone");

  @ArchTest
  static void noNewReachIntoAnotherModulesInternals(JavaClasses classes) {
    checkAgainstTheStore(moduleInternalsStayInTheirModule, classes);
  }

  @ArchTest
  static void noNewSystemClockOutsideConfig(JavaClasses classes) {
    checkAgainstTheStore(theSystemClockIsReadInConfigAlone, classes);
  }

  private static void checkAgainstTheStore(ArchRule rule, JavaClasses classes) {
    FreezingArchRule.freeze(rule).check(classes);
    int ceiling = CEILINGS.get(rule.getDescription());
    assertThat(rule.evaluate(classes).getFailureReport().getDetails())
        .as(
            "%s has a ceiling of %d. When a violation is fixed, commit the shrunken store and lower"
                + " the ceiling to match; a count above it means the store was refrozen.",
            rule.getDescription(), ceiling)
        .hasSize(ceiling);
  }

  private static ArchCondition<JavaClass>
      reachAnotherModuleOnlyThroughItsApplicationOrDomainValues() {
    return new ArchCondition<>(
        "reach another module only through its application or domain values") {
      @Override
      public void check(JavaClass from, ConditionEvents events) {
        String module = module(from.getPackageName());
        if (module == null) {
          return;
        }
        from.getDirectDependenciesFromSelf().stream()
            .map(Dependency::getTargetClass)
            .map(JavaClass::getBaseComponentType)
            .filter(ArchUnitRatchetRulesTest::isInternal)
            .filter(target -> !module.equals(module(target.getPackageName())))
            .filter(target -> module(target.getPackageName()) != null)
            .map(JavaClass::getName)
            .distinct()
            .sorted()
            .forEach(
                target ->
                    events.add(
                        SimpleConditionEvent.violated(
                            from, from.getName() + " reaches " + target)));
      }
    };
  }

  private static boolean isInternal(JavaClass target) {
    return INTERNAL.matcher(target.getPackageName()).find() || target.isAnnotatedWith(Entity.class);
  }

  private static ArchCondition<JavaClass> takeTheClockBeanInsteadOfTheSystemClock() {
    return new ArchCondition<>("take the Clock bean instead of the system clock") {
      @Override
      public void check(JavaClass from, ConditionEvents events) {
        from.getMethodCallsFromSelf().stream()
            .filter(call -> call.getTargetOwner().isEquivalentTo(Clock.class))
            .map(call -> call.getTarget().getName())
            .filter(SYSTEM_CLOCKS::contains)
            .distinct()
            .sorted()
            .forEach(
                method ->
                    events.add(
                        SimpleConditionEvent.violated(
                            from, from.getName() + " calls Clock." + method + "()")));
      }
    };
  }

  static String module(String packageName) {
    for (String root : MODULE_ROOTS) {
      if (packageName.startsWith(root)) {
        String rest = packageName.substring(root.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
      }
    }
    return null;
  }
}
