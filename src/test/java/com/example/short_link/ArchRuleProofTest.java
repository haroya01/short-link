package com.example.short_link;

import static com.example.short_link.ArchRuleProofs.PROOFS;
import static com.example.short_link.ArchRuleProofs.RULES_WITHOUT_A_PROOF;
import static com.example.short_link.ArchRuleProofs.RULES_WITHOUT_A_PROOF_BASELINE;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.ArchRuleProofs.Proof;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ArchRuleProofTest {

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
  void everyFixtureStillProvesThatItsRuleDiscriminates() {
    for (Proof proof : PROOFS) {
      for (Class<?> flagged : proof.flagged()) {
        assertThat(violations(proof.check(), flagged))
            .as("%s no longer catches %s", proof.rule(), flagged.getSimpleName())
            .isNotEmpty();
      }
      for (Class<?> clean : proof.clean()) {
        assertThat(violations(proof.check(), clean))
            .as("%s now also catches %s", proof.rule(), clean.getSimpleName())
            .isEmpty();
      }
    }
  }

  @Test
  void everyProofChecksTheRuleItNames() {
    Map<String, ArchRule> declared = declaredRules();

    for (Proof proof : PROOFS) {
      assertThat(declared)
          .as("No rule is declared under this name: %s", proof.rule())
          .containsKey(proof.rule());
      assertThat(proof.check())
          .as("%s proves a different rule than the one it names", proof.rule())
          .isSameAs(declared.get(proof.rule()));
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
        .as(
            "Baseline %d — prove a rule with a fixture, then lower RULES_WITHOUT_A_PROOF_BASELINE.",
            RULES_WITHOUT_A_PROOF_BASELINE)
        .hasSizeLessThanOrEqualTo(RULES_WITHOUT_A_PROOF_BASELINE);
  }

  private static List<String> violations(ArchRule rule, Class<?> fixture) {
    JavaClasses classes = new ClassFileImporter().importClasses(fixture);
    // A fixture the rule does not select is not a violation; that is what the clean side checks.
    return rule.allowEmptyShould(true).evaluate(classes).getFailureReport().getDetails();
  }

  private static Set<String> provenRules() {
    return PROOFS.stream().map(Proof::rule).collect(Collectors.toSet());
  }

  private static Map<String, ArchRule> declaredRules() {
    JavaClasses tests =
        new ClassFileImporter()
            .withImportOption(new ImportOption.OnlyIncludeTests())
            .importPackages("com.example.short_link");
    Map<String, ArchRule> declared = new TreeMap<>();
    for (JavaClass holder : tests) {
      if (!holder.isAnnotatedWith(AnalyzeClasses.class)) {
        continue;
      }
      Class<?> loaded = holder.reflect();
      for (Field field : loaded.getDeclaredFields()) {
        if (ArchRule.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          declared.put(loaded.getSimpleName() + "." + field.getName(), read(field));
        }
      }
    }
    return declared;
  }

  private static ArchRule read(Field field) {
    try {
      return (ArchRule) field.get(null);
    } catch (IllegalAccessException unreadable) {
      throw new AssertionError("Unreadable rule field: " + field, unreadable);
    }
  }
}
