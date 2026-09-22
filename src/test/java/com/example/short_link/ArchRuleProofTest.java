package com.example.short_link;

import static com.example.short_link.ArchRuleProofs.PROOFS;
import static com.example.short_link.ArchRuleProofs.RULES_WITHOUT_A_PROOF;
import static com.example.short_link.ArchRuleProofs.RULES_WITHOUT_A_PROOF_BASELINE;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.ArchRuleProofs.Proof;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ArchRuleProofTest {

  private static final String FIXTURE_PACKAGE = "archfixtures";

  private static final Map<Class<?>, JavaClasses> SCANS = new HashMap<>();

  record Declared(Class<?> holder, ArchRule rule, boolean enforced) {}

  @Test
  void aNewRuleCannotShipWithoutAFixtureThatItCatches() {
    Set<String> unproven = new TreeSet<>(declaredRules().keySet());
    unproven.removeAll(provenRules());
    unproven.removeAll(RULES_WITHOUT_A_PROOF);

    assertThat(unproven)
        .as(
            "A rule ships with an example that breaks it. Register the classes it must flag, "
                + "and the near miss it must leave alone, in ArchRuleProofs.PROOFS.")
        .isEmpty();
  }

  @Test
  void everyDeclaredRuleIsEnforced() {
    Set<String> unenforced =
        declaredRules().entrySet().stream()
            .filter(entry -> !entry.getValue().enforced())
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(unenforced)
        .as(
            "A rule nothing runs guards nothing. Annotate the field with @ArchTest, "
                + "or evaluate it from an @ArchTest method.")
        .isEmpty();
  }

  @Test
  void everyFixtureStillProvesThatItsRuleDiscriminates() {
    Map<String, Declared> declared = declaredRules();

    for (Proof proof : PROOFS) {
      JavaClasses scanned = enforcementScanOf(declared.get(proof.rule()).holder());
      for (Class<?> flagged : proof.flagged()) {
        assertThat(violations(proof.check(), scanned, flagged))
            .as("%s no longer catches %s", proof.rule(), flagged.getSimpleName())
            .isNotEmpty();
      }
      for (Class<?> clean : proof.clean()) {
        assertThat(violations(proof.check(), scanned, clean))
            .as("%s now also catches %s", proof.rule(), clean.getSimpleName())
            .isEmpty();
      }
    }
  }

  @Test
  void everyProofChecksTheRuleItNames() {
    Map<String, Declared> declared = declaredRules();

    for (Proof proof : PROOFS) {
      assertThat(declared)
          .as("No rule is declared under this name: %s", proof.rule())
          .containsKey(proof.rule());
      assertThat(proof.check())
          .as("%s proves a different rule than the one it names", proof.rule())
          .isSameAs(declared.get(proof.rule()).rule());
    }
  }

  @Test
  void theListOfUnprovenRulesKeepsNoNamesOfRulesThatAreGone() {
    Set<String> stale = new TreeSet<>(RULES_WITHOUT_A_PROOF);
    stale.removeAll(declaredRules().keySet());

    assertThat(stale).as("Names of rules that no longer exist").isEmpty();
  }

  @Test
  void theListOfUnprovenRulesOnlyShrinks() {
    assertThat(RULES_WITHOUT_A_PROOF)
        .as("Names only leave RULES_WITHOUT_A_PROOF. A new rule ships with a proof instead.")
        .isSubsetOf(RULES_WITHOUT_A_PROOF_BASELINE);

    Set<String> provenButStillListed = new TreeSet<>(RULES_WITHOUT_A_PROOF);
    provenButStillListed.retainAll(provenRules());
    assertThat(provenButStillListed).as("A proven rule leaves RULES_WITHOUT_A_PROOF.").isEmpty();
  }

  private static List<String> violations(ArchRule rule, JavaClasses scanned, Class<?> fixture) {
    JavaClasses only = scanned.that(equivalentTo(fixture));
    assertThat(only).as("%s is missing from the scan", fixture.getName()).isNotEmpty();
    // A fixture the rule does not select is not a violation; that is what the clean side checks.
    return rule.allowEmptyShould(true).evaluate(only).getFailureReport().getDetails();
  }

  // The fixture is judged inside the same scan the rule runs in, so the proof sees exactly
  // what the rule sees: the same packages, the same import options, the same unresolved types.
  private static JavaClasses enforcementScanOf(Class<?> holder) {
    return SCANS.computeIfAbsent(holder, ArchRuleProofTest::scanLikeTheEngine);
  }

  private static JavaClasses scanLikeTheEngine(Class<?> holder) {
    AnalyzeClasses analyze = holder.getAnnotation(AnalyzeClasses.class);
    List<ImportOption> options = new ArrayList<>();
    for (Class<? extends ImportOption> option : analyze.importOptions()) {
      options.add(instantiate(option));
    }
    List<String> packages = new ArrayList<>(List.of(analyze.packages()));
    for (Class<?> anchor : analyze.packagesOf()) {
      packages.add(anchor.getPackageName());
    }
    packages.add(FIXTURE_PACKAGE);
    return new ClassFileImporter()
        .withImportOption(
            location -> isFixture(location) || options.stream().allMatch(o -> o.includes(location)))
        .importPackages(packages);
  }

  private static boolean isFixture(Location location) {
    return location.contains("/" + FIXTURE_PACKAGE + "/");
  }

  private static ImportOption instantiate(Class<? extends ImportOption> option) {
    try {
      return option.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException unconstructable) {
      throw new AssertionError(
          "Import option without a public no-arg constructor: " + option, unconstructable);
    }
  }

  private static Set<String> provenRules() {
    return PROOFS.stream().map(Proof::rule).collect(Collectors.toSet());
  }

  private static Map<String, Declared> declaredRules() {
    JavaClasses tests =
        new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages("com.example.short_link");
    Map<String, Declared> declared = new TreeMap<>();
    for (JavaClass holder : tests) {
      if (!holder.isAnnotatedWith(AnalyzeClasses.class)) {
        continue;
      }
      Set<String> readByArchTests = fieldsReadByArchTests(holder);
      Class<?> loaded = holder.reflect();
      for (Field field : loaded.getDeclaredFields()) {
        if (!ArchRule.class.isAssignableFrom(field.getType())) {
          continue;
        }
        field.setAccessible(true);
        boolean enforced =
            field.isAnnotationPresent(ArchTest.class) || readByArchTests.contains(field.getName());
        declared.put(
            loaded.getSimpleName() + "." + field.getName(),
            new Declared(loaded, read(field), enforced));
      }
    }
    return declared;
  }

  private static Set<String> fieldsReadByArchTests(JavaClass holder) {
    return holder.getMethods().stream()
        .filter(method -> method.isAnnotatedWith(ArchTest.class))
        .flatMap(method -> method.getFieldAccesses().stream())
        .filter(access -> access.getTargetOwner().equals(holder))
        .map(access -> access.getTarget().getName())
        .collect(Collectors.toSet());
  }

  private static ArchRule read(Field field) {
    try {
      return (ArchRule) field.get(null);
    } catch (IllegalAccessException unreadable) {
      throw new AssertionError("Unreadable rule field: " + field, unreadable);
    }
  }
}
