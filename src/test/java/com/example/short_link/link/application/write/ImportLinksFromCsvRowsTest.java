package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.BulkImportResult;
import com.example.short_link.link.application.dto.BulkImportRow;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportLinksFromCsvRowsTest {

  private static final Long OWNER = 3L;
  private static final ShortCode CREATED = new ShortCode("made001");

  @Mock private CreateLinkUseCase creation;
  @Captor private ArgumentCaptor<CreateLinkCommand> created;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private ImportLinksFromCsvUseCase useCase;

  @BeforeEach
  void aUseCase() {
    useCase = new ImportLinksFromCsvUseCase(creation, meters);
  }

  @Test
  void aBatchOfExactlyTheRowLimitIsImported() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    BulkImportResult result = run(rows(ImportLinksFromCsvUseCase.MAX_ROWS));

    assertThat(result.ok()).isEqualTo(ImportLinksFromCsvUseCase.MAX_ROWS);
    assertThat(result.failed()).isZero();
  }

  @Test
  void oneRowOverTheLimitRejectsTheWholeBatchBeforeAnyLinkIsMade() {
    assertThatThrownBy(() -> run(rows(ImportLinksFromCsvUseCase.MAX_ROWS + 1)))
        .isInstanceOfSatisfying(
            LinkException.class,
            failure ->
                assertThat(failure.errorCode()).isEqualTo(LinkErrorCode.BULK_IMPORT_TOO_LARGE));
    verify(creation, never()).execute(any());
  }

  @Test
  void aRowCarriesItsTrimmedUrlCodeAndExpiryIntoTheNewLink() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    BulkImportResult result =
        run(
            "url,custom_code,expires_at\n https://example.com/a , spring , 2027-01-02T03:04:05Z \n");

    verify(creation).execute(created.capture());
    assertThat(created.getValue())
        .isEqualTo(
            CreateLinkCommand.of(
                "https://example.com/a", OWNER, "spring", Instant.parse("2027-01-02T03:04:05Z")));
    assertThat(result.rows())
        .containsExactly(
            new BulkImportRow(
                "https://example.com/a", "spring", "2027-01-02T03:04:05Z", CREATED, null));
  }

  @Test
  void columnsTheFileDoesNotHaveStayAbsentInTheLinkAndInTheEchoedRow() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    BulkImportResult result = run("url\nhttps://example.com/only-url\n");

    verify(creation).execute(created.capture());
    assertThat(created.getValue())
        .isEqualTo(CreateLinkCommand.of("https://example.com/only-url", OWNER, null, null));
    assertThat(result.rows())
        .containsExactly(
            new BulkImportRow("https://example.com/only-url", null, null, CREATED, null));
  }

  @Test
  void aFileWithoutAHeaderReadsUrlCodeAndExpiryByPosition() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    run("https://example.com/a,spring,2027-01-02T03:04:05Z\n");

    verify(creation).execute(created.capture());
    assertThat(created.getValue())
        .isEqualTo(
            CreateLinkCommand.of(
                "https://example.com/a", OWNER, "spring", Instant.parse("2027-01-02T03:04:05Z")));
  }

  @Test
  void aRowShorterThanTheHeaderLeavesItsMissingCellsAbsent() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    BulkImportResult result = run("url,custom_code\nhttps://example.com/short-row\n");

    assertThat(result.rows())
        .containsExactly(
            new BulkImportRow("https://example.com/short-row", null, null, CREATED, null));
  }

  @Test
  void theRowMetersCountWhatWasMadeAndWhatFailed() throws Exception {
    when(creation.execute(any())).thenReturn(new LinkCreated(CREATED));

    BulkImportResult result =
        run("url\nhttps://example.com/1\nhttps://example.com/2\nftp://example.com/3\n");

    assertThat(result.ok()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(meters.counter("bulk_import.rows", "result", "ok").count()).isEqualTo(2.0);
    assertThat(meters.counter("bulk_import.rows", "result", "failed").count()).isEqualTo(1.0);
  }

  private BulkImportResult run(String csv) throws Exception {
    return useCase.execute(
        new ImportLinksFromCsvCommand(
            OWNER, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
  }

  private static String rows(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> "https://example.com/" + i)
        .collect(Collectors.joining("\n", "url\n", "\n"));
  }
}
