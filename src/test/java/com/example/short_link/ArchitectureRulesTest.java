package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private static final Path MAIN = Path.of("src/main/java/com/example/short_link");
  private static final Path TEST = Path.of("src/test/java/com/example/short_link");

  /**
   * Ratchet for migrating ControllerTest classes from {@code @SpringBootTest} to
   * {@code @WebMvcTest}. Slice tests boot only the MVC layer + the controller's declared
   * dependencies (~50ms vs ~3-5s for a full {@code @SpringBootTest}). A new {@code @SpringBootTest}
   * ControllerTest pushes the count past baseline and fails the build — migrate an existing one to
   * balance, or raise the baseline only with a deliberate decision.
   */
  private static final int SPRING_BOOT_CONTROLLER_TEST_BASELINE = 36;

  private static final Pattern FEATURE_PRESENTATION_IMPORT =
      Pattern.compile(
          "^import com\\.example\\.short_link\\.(admin|billing|campaign|link|profile|user)\\.presentation\\.",
          Pattern.MULTILINE);

  private static final Pattern REPOSITORY_IMPORT =
      Pattern.compile("^import com\\.example\\.short_link\\..*Repository;", Pattern.MULTILINE);

  private static final Pattern PRESENTATION_DTO_FILE =
      Pattern.compile(".*(Request|Response|Page|ProblemDetails)\\.java$");

  private static final Pattern NESTED_PRESENTATION_DTO =
      Pattern.compile("\\brecord\\s+\\w*(Request|Response|Page)\\b");

  @Test
  void applicationLayerDoesNotDependOnFeaturePresentationLayer() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> relative(path).contains("/application/"))
            .filter(path -> FEATURE_PRESENTATION_IMPORT.matcher(read(path)).find())
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void controllersDoNotDependOnRepositoriesDirectly() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
            .filter(path -> REPOSITORY_IMPORT.matcher(read(path)).find())
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void exceptionHandlersLiveInAdviceClasses() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> read(path).contains("@ExceptionHandler"))
            .filter(path -> !path.getFileName().toString().endsWith("ExceptionHandler.java"))
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void unmatchedRequestsAreNotPermittedByDefault() throws IOException {
    String securityConfig = Files.readString(MAIN.resolve("user/config/SecurityConfig.java"));

    assertThat(securityConfig).doesNotContainPattern("(?s)\\.anyRequest\\(\\)\\s*\\.permitAll\\(");
  }

  @Test
  void presentationDtosLiveInRequestResponseOrSubFeaturePackages() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> relative(path).contains("/presentation/"))
            .filter(path -> PRESENTATION_DTO_FILE.matcher(path.getFileName().toString()).matches())
            .filter(
                path -> {
                  String relative = relative(path);
                  if (relative.contains("/presentation/request/")
                      || relative.contains("/presentation/response/")) {
                    return false;
                  }
                  // sub-feature folder (e.g., /presentation/email/MyEmailLeadResponse.java) is OK.
                  // Violation = file directly under /presentation/ (matches
                  // /presentation/Foo.java).
                  return relative.matches(".*/presentation/[A-Z][A-Za-z0-9]*\\.java$");
                })
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void applicationDtosDoNotUsePresentationSuffixes() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> relative(path).contains("/application/"))
            .filter(path -> PRESENTATION_DTO_FILE.matcher(path.getFileName().toString()).matches())
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void presentationControllersDoNotDeclareRequestOrResponseDtosInline() throws IOException {
    List<String> violations =
        javaSources()
            .filter(path -> relative(path).contains("/presentation/"))
            .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
            .filter(path -> NESTED_PRESENTATION_DTO.matcher(read(path)).find())
            .map(ArchitectureRulesTest::relative)
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  void newControllerTestsShouldUseWebMvcTestNotSpringBootTest() throws IOException {
    long springBootControllerTests;
    try (Stream<Path> tests = Files.walk(TEST)) {
      springBootControllerTests =
          tests
              .filter(path -> path.getFileName().toString().endsWith("ControllerTest.java"))
              .filter(path -> read(path).contains("@SpringBootTest"))
              .count();
    }

    assertThat(springBootControllerTests)
        .as(
            "Baseline %d — new ControllerTest must use @WebMvcTest. Migrate an existing one "
                + "to drop below baseline, or update SPRING_BOOT_CONTROLLER_TEST_BASELINE if "
                + "the new test legitimately needs the full ApplicationContext.",
            SPRING_BOOT_CONTROLLER_TEST_BASELINE)
        .isLessThanOrEqualTo(SPRING_BOOT_CONTROLLER_TEST_BASELINE);
  }

  private static Stream<Path> javaSources() throws IOException {
    return Files.walk(MAIN).filter(path -> path.toString().endsWith(".java"));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String relative(Path path) {
    return MAIN.getParent().getParent().getParent().getParent().relativize(path).toString();
  }
}
