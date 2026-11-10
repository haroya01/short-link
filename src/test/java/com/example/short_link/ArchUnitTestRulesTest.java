package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.TestcontainersExtension;

// The mutation job (build.gradle, -PmutationScope=core) has no MySQL or Redis and skips tests
// tagged "integration". Extensions and tags are looked up the way JUnit looks them up.
@AnalyzeClasses(
    packages = {"com.example.short_link", "db"},
    importOptions = {ImportOption.OnlyIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchUnitTestRulesTest {

  static final String INTEGRATION = "integration";

  private static final Set<Class<?>> STARTS_SPRING_OR_A_CONTAINER =
      Set.of(SpringExtension.class, TestcontainersExtension.class);

  @ArchTest
  static final ArchRule testsThatStartSpringOrAContainerAreTaggedIntegration =
      classes().that(startSpringOrAContainer()).should(beTagged(INTEGRATION));

  private static DescribedPredicate<JavaClass> startSpringOrAContainer() {
    return DescribedPredicate.describe(
        "start Spring or a container",
        javaClass ->
            AnnotationSupport.findRepeatableAnnotations(javaClass.reflect(), ExtendWith.class)
                .stream()
                .flatMap(extendWith -> Arrays.stream(extendWith.value()))
                .anyMatch(STARTS_SPRING_OR_A_CONTAINER::contains));
  }

  private static ArchCondition<JavaClass> beTagged(String tag) {
    return new ArchCondition<>("carry @Tag(\"" + tag + "\")") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean tagged =
            AnnotationSupport.findRepeatableAnnotations(javaClass.reflect(), Tag.class).stream()
                .anyMatch(found -> found.value().equals(tag));
        if (!tagged) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " starts Spring or a container but has no @Tag(\""
                      + tag
                      + "\"), so the mutation job would run it without a database"));
        }
      }
    };
  }
}
