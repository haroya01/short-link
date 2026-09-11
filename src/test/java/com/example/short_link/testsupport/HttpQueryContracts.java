package com.example.short_link.testsupport;

import io.queryaudit.core.interceptor.QueryCaptureSnapshot;
import io.queryaudit.core.interceptor.QueryInterceptor;
import io.queryaudit.core.model.QueryRecord;
import io.queryaudit.core.regression.QueryContracts;
import io.queryaudit.core.regression.QueryCountBaseline;
import io.queryaudit.core.regression.QueryCounts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures an HTTP round trip or an explicit worker invocation, excluding fixture setup and
 * database assertions. Journeys can await their real background queues before capture ends.
 *
 * <p>QueryAudit 0.6 captures every thread using the wrapped DataSource while this window is open.
 * Use an isolated database with background scheduling disabled and run these tests sequentially.
 * This helper owns capture directly; its tests must not also use {@code @QueryAudit}.
 */
public final class HttpQueryContracts {

  private static final Path CONTRACTS_DIRECTORY = Path.of("src/test/resources/query-contracts");
  private static final Path REPORT_DIRECTORY = Path.of("build/reports/query-audit/http-contracts");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final QueryInterceptor interceptor;
  private final Runnable awaitCompletion;
  private final String scope;

  public HttpQueryContracts(QueryInterceptor interceptor) {
    this.interceptor = interceptor;
    this.awaitCompletion = () -> {};
    this.scope = "synchronous-http-round-trip";
  }

  public HttpQueryContracts(QueryInterceptor interceptor, Runnable awaitCompletion) {
    this.interceptor = interceptor;
    this.awaitCompletion = awaitCompletion;
    this.scope = "http-round-trip-and-triggered-async-work";
  }

  public <T> CapturedRequest<T> capture(String contractId, Callable<T> request) throws Exception {
    return captureWithin(contractId, request, scope);
  }

  public <T> CapturedRequest<T> captureBackgroundDelivery(String contractId, Callable<T> delivery)
      throws Exception {
    return captureWithin(contractId, delivery, "explicit-background-delivery");
  }

  private <T> CapturedRequest<T> captureWithin(
      String contractId, Callable<T> request, String capturedScope) throws Exception {
    if (!contractId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
      throw new IllegalArgumentException(
          "HTTP contract IDs must contain lowercase words and hyphens");
    }
    if (interceptor.isActive()) {
      throw new IllegalStateException("An HTTP query capture is already running");
    }

    T response;
    interceptor.start();
    try {
      response = request.call();
      awaitCompletion.run();
    } finally {
      interceptor.stop();
    }

    QueryCaptureSnapshot snapshot = interceptor.snapshot();
    CapturedRequest<T> captured = new CapturedRequest<>(contractId, response, snapshot);
    writeReport(captured, capturedScope);
    if (snapshot.truncated()) {
      throw new AssertionError(
          "HTTP query capture discarded "
              + snapshot.droppedCount()
              + " queries; an incomplete capture cannot verify a contract");
    }
    return captured;
  }

  /** Checks the reviewed exact counts. This method never records or updates contracts. */
  public void verify(CapturedRequest<?> captured) {
    String contractId = captured.contractId();
    Map<String, QueryCounts> contracts = loadContracts();
    if (!contracts.containsKey(QueryCountBaseline.key(contractId))) {
      throw new AssertionError(
          "Missing HTTP query contract: " + contractId + " in " + CONTRACTS_DIRECTORY);
    }

    String failure =
        QueryContracts.verify(
            contractId, "HTTP", contractId, captured.counts(), contracts, captured.queries());
    if (failure != null) {
      throw new AssertionError(failure);
    }
  }

  private static Map<String, QueryCounts> loadContracts() {
    Map<String, QueryCounts> contracts = new LinkedHashMap<>();
    try (var files = Files.list(CONTRACTS_DIRECTORY)) {
      for (Path file :
          files.filter(path -> path.toString().endsWith(".contracts")).sorted().toList()) {
        QueryCountBaseline.load(file)
            .forEach(
                (id, counts) -> {
                  if (contracts.putIfAbsent(id, counts) != null) {
                    throw new AssertionError(
                        "Duplicate HTTP query contract: " + id + " in " + file);
                  }
                });
      }
    } catch (IOException failure) {
      throw new AssertionError("Cannot read HTTP query contracts", failure);
    }
    return contracts;
  }

  private static void writeReport(CapturedRequest<?> captured, String scope) {
    String contractId = captured.contractId();
    Path reportFile = REPORT_DIRECTORY.resolve(contractId + ".json");
    Map<String, Object> report =
        Map.of(
            "contractId", contractId,
            "scope", scope,
            "counts", captured.counts(),
            "droppedQueryCount", captured.snapshot().droppedCount(),
            "queries", captured.queries());
    try {
      Files.createDirectories(REPORT_DIRECTORY);
      Files.writeString(
          reportFile, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    } catch (IOException failure) {
      throw new AssertionError("Could not write HTTP query evidence: " + reportFile, failure);
    }
  }

  public record CapturedRequest<T>(String contractId, T response, QueryCaptureSnapshot snapshot) {

    public List<QueryRecord> queries() {
      return snapshot.queries();
    }

    public QueryCounts counts() {
      return QueryCounts.from(queries());
    }
  }
}
