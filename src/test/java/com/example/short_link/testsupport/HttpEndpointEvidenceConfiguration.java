package com.example.short_link.testsupport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/** Records which real server handlers each journey exercised, independent of test method names. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class HttpEndpointEvidenceConfiguration implements WebMvcConfigurer {

  private static final Path DIRECTORY = Path.of("build/reports/query-audit/http-contracts");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new AsyncHandlerInterceptor() {
          @Override
          public void afterConcurrentHandlingStarted(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            record(request, response, handler);
          }

          @Override
          public void afterCompletion(
              HttpServletRequest request,
              HttpServletResponse response,
              Object handler,
              Exception failure) {
            record(request, response, handler);
          }

          private void record(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            String id = request.getHeader("X-Query-Contract-ID");
            if (id == null
                || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || !(handler instanceof HandlerMethod method)) {
              return;
            }
            write(
                DIRECTORY.resolve("routes").resolve(id + ".json"),
                Map.of(
                    "contractId", id,
                    "handler", handlerId(method),
                    "method", request.getMethod(),
                    "pattern",
                        String.valueOf(
                            request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
                    "status", response.getStatus()));
          }
        });
  }

  @EventListener
  public void recordActualServerEndpoints(WebServerInitializedEvent event) {
    var context = event.getApplicationContext();
    if (!context.containsBean("requestMappingHandlerMapping")) {
      return;
    }
    var mapping =
        context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
    List<Map<String, Object>> endpoints = new ArrayList<>();
    mapping
        .getHandlerMethods()
        .forEach(
            (route, handler) -> {
              if (handler.getBeanType().getPackageName().startsWith("com.example.short_link")) {
                endpoints.add(
                    Map.of(
                        "handler", handlerId(handler),
                        "methods",
                            route.getMethodsCondition().getMethods().stream()
                                .map(Enum::name)
                                .sorted()
                                .toList(),
                        "patterns", route.getPatternValues().stream().sorted().toList()));
              }
            });
    endpoints.sort(Comparator.comparing(endpoint -> endpoint.get("handler").toString()));
    write(DIRECTORY.resolve("endpoint-inventory.json"), endpoints);
  }

  private static String handlerId(HandlerMethod method) {
    return method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
  }

  private static void write(Path destination, Object value) {
    try {
      Files.createDirectories(destination.getParent());
      Files.writeString(
          destination, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Could not write HTTP endpoint evidence: " + destination, failure);
    }
  }
}
