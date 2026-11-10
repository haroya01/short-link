package archfixtures;

import com.example.short_link.testsupport.KurlWebMvcTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Testcontainers;

// No test methods, so JUnit discovers none of these and nothing starts.
public final class IntegrationTagFixtures {

  private IntegrationTagFixtures() {}

  @SpringBootTest
  public static class StartsSpringUntagged {}

  @SpringJUnitConfig
  public static class StartsASmallContextUntagged {}

  @Testcontainers
  public static class StartsAContainerUntagged {}

  @SpringBootTest
  @Tag("integration")
  public static class StartsSpringTagged {}

  public static class InheritsATaggedSpringBase extends StartsSpringTagged {}

  @KurlWebMvcTest
  public static class StartsASliceUnderATaggedAnnotation {}

  @ExtendWith(MockitoExtension.class)
  public static class UsesMocksOnly {}
}
