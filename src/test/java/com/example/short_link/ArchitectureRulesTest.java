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
