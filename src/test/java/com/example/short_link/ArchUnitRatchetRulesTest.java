package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.repository.Repository;
import org.springframework.scheduling.annotation.Async;

// Frozen rules keep today's violations in src/test/resources/archunit_store and fail on new ones;
// the ceiling pins how many there are, so the count only comes down.
@AnalyzeClasses(
    packages = "com.example.short_link",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchUnitRatchetRulesTest {

  static final Map<String, Integer> CEILINGS =
      Map.of(
          "moduleInternalsStayInTheirModule", 6,
          "theSystemClockIsReadInConfigAlone", 0,
          "transactionsDoNotReachOutboundClients", 22,
          "springDataRepositoriesStayBehindPorts", 0,
          "constructorCallsPassFewerThanTenArguments", 135);

  private static final List<String> MODULE_ROOTS =
      List.of("com.example.short_link.", "archfixtures.modules.");

  private static final Pattern INTERNAL =
      Pattern.compile("\\.(infrastructure|domain\\.repository)(\\.|$)");

  private static final Set<String> SYSTEM_CLOCKS =
      Set.of("systemUTC", "systemDefaultZone", "system");

  private static final int LONG_PARAMETER_LIST = 10;

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

  static final ArchRule transactionsDoNotReachOutboundClients =
      methods()
          .that(ArchUnitSemanticRulesTest.areTransactional())
          .should(notReachAnOutboundClientThroughTheMethodsTheyCall())
          .as("transactionsDoNotReachOutboundClients");

  static final ArchRule springDataRepositoriesStayBehindPorts =
      classes()
          .that()
          .resideInAnyPackage("..application..", "..domain..")
          .should(reachPersistenceOnlyThroughADomainPort())
          .as("springDataRepositoriesStayBehindPorts");

  static final ArchRule constructorCallsPassFewerThanTenArguments =
      classes()
          .should(callConstructorsThatTakeFewerThanTenParameters())
          .as("constructorCallsPassFewerThanTenArguments");

  @ArchTest
  static void noNewOutboundCallReachedFromATransaction(JavaClasses classes) {
    checkAgainstTheStore(transactionsDoNotReachOutboundClients, classes);
  }

  @ArchTest
  static void noNewReachIntoAnotherModulesInternals(JavaClasses classes) {
    checkAgainstTheStore(moduleInternalsStayInTheirModule, classes);
  }

  @ArchTest
  static void noNewSystemClockOutsideConfig(JavaClasses classes) {
    checkAgainstTheStore(theSystemClockIsReadInConfigAlone, classes);
  }

  @ArchTest
  static void noNewSpringDataRepositoryInApplicationOrDomain(JavaClasses classes) {
    checkAgainstTheStore(springDataRepositoriesStayBehindPorts, classes);
  }

  @ArchTest
  static void noNewConstructorCallWithTenOrMoreArguments(JavaClasses classes) {
    checkAgainstTheStore(constructorCallsPassFewerThanTenArguments, classes);
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

  private static ArchCondition<JavaClass> reachPersistenceOnlyThroughADomainPort() {
    return new ArchCondition<>("reach persistence only through a domain port") {
      @Override
      public void check(JavaClass from, ConditionEvents events) {
        from.getDirectDependenciesFromSelf().stream()
            .map(Dependency::getTargetClass)
            .map(JavaClass::getBaseComponentType)
            .filter(target -> target.isAssignableTo(Repository.class))
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

  private static ArchCondition<JavaMethod> notReachAnOutboundClientThroughTheMethodsTheyCall() {
    return new ArchCondition<>("not reach an outbound client through the methods they call") {
      private final Set<JavaCodeUnit> reaching = new HashSet<>();
      private final Set<JavaCodeUnit> clear = new HashSet<>();

      @Override
      public void init(Collection<JavaMethod> allObjectsToTest) {
        reaching.clear();
        clear.clear();
      }

      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (reach(method, new HashSet<>()) == Reach.YES) {
          events.add(
              SimpleConditionEvent.violated(
                  method, method.getFullName() + " reaches an outbound client"));
        }
      }

      private Reach reach(JavaCodeUnit unit, Set<JavaCodeUnit> path) {
        if (reaching.contains(unit)) {
          return Reach.YES;
        }
        if (clear.contains(unit)) {
          return Reach.NO;
        }
        if (!path.add(unit)) {
          return Reach.CUT;
        }
        boolean cut = false;
        try {
          for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
            if (ArchUnitSemanticRulesTest.OUTBOUND_CLIENTS.contains(
                call.getTargetOwner().getName())) {
              reaching.add(unit);
              return Reach.YES;
            }
            for (JavaMethod target : landingsOf(call)) {
              Reach deeper = reach(target, path);
              if (deeper == Reach.YES) {
                reaching.add(unit);
                return Reach.YES;
              }
              cut |= deeper == Reach.CUT;
            }
          }
        } finally {
          path.remove(unit);
        }
        if (cut) {
          return Reach.CUT;
        }
        clear.add(unit);
        return Reach.NO;
      }
    };
  }

  private static ArchCondition<JavaClass> callConstructorsThatTakeFewerThanTenParameters() {
    return new ArchCondition<>("call constructors that take fewer than ten parameters") {
      @Override
      public void check(JavaClass from, ConditionEvents events) {
        from.getConstructorCallsFromSelf().stream()
            .filter(call -> call.getTarget().getRawParameterTypes().size() >= LONG_PARAMETER_LIST)
            .map(
                call ->
                    call.getOriginOwner().getName()
                        + "."
                        + call.getOrigin().getName()
                        + " calls the constructor of "
                        + call.getTargetOwner().getName())
            .distinct()
            .sorted()
            .forEach(line -> events.add(SimpleConditionEvent.violated(from, line)));
      }
    };
  }

  private enum Reach {
    YES,
    NO,
    CUT
  }

  private static List<JavaMethod> landingsOf(JavaMethodCall call) {
    JavaClass owner = call.getTargetOwner();
    if (module(owner.getPackageName()) == null
        && !owner.getPackageName().startsWith("archfixtures")) {
      return List.of();
    }
    String[] parameters =
        call.getTarget().getRawParameterTypes().stream()
            .map(JavaClass::getName)
            .toArray(String[]::new);
    List<JavaMethod> landings = new ArrayList<>();
    call.getTarget().resolveMember().ifPresent(landings::add);
    for (JavaClass implementation : owner.getAllSubclasses()) {
      implementation.tryGetMethod(call.getName(), parameters).ifPresent(landings::add);
    }
    // An @Async method runs on another thread, and a NOT_SUPPORTED or NEVER one suspends or
    // refuses the caller's transaction, so neither runs inside it.
    landings.removeIf(
        method ->
            method.isAnnotatedWith(Async.class)
                || ArchUnitSemanticRulesTest.runsWithoutATransaction(method));
    return landings;
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
