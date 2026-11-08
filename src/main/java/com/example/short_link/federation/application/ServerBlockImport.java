package com.example.short_link.federation.application;

import com.example.short_link.common.csv.CsvRows;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// A shared list in Mastodon's domain_blocks.csv form. A server already blocked here keeps the
// moderators' own decision, so the list only adds servers; each one is its own block, as if a
// moderator had entered it.
@Service
@RequiredArgsConstructor
public class ServerBlockImport {

  static final int MAX_SERVERS = 1_000;
  private static final int MAX_LISTED_INVALID = 100;
  private static final int MAX_REASON = 500;

  private final ServerBlocks serverBlocks;
  private final FederationUrls urls;

  public record Imported(int added, int kept, int skipped, List<String> invalid) {}

  public Imported importList(String csv, boolean dryRun) {
    List<List<String>> rows = CsvRows.parse(csv == null ? "" : csv);
    Columns columns = columns(rows);
    Set<String> blocked = new HashSet<>();
    serverBlocks.list().forEach(block -> blocked.add(block.domain()));
    int added = 0;
    int kept = 0;
    int skipped = 0;
    List<String> invalid = new ArrayList<>();
    for (List<String> row : rows.subList(1, rows.size())) {
      ServerBlockSeverity severity = severity(cell(row, columns.severity()));
      String domain = severity == null ? null : domain(cell(row, columns.domain()), invalid);
      if (domain == null) {
        skipped++;
        continue;
      }
      if (!blocked.add(domain)) {
        kept++;
        continue;
      }
      if (!dryRun) {
        serverBlocks.block(domain, severity, reason(cell(row, columns.comment())));
      }
      added++;
    }
    return new Imported(added, kept, skipped, List.copyOf(invalid));
  }

  private record Columns(int domain, int severity, int comment) {}

  private static Columns columns(List<List<String>> rows) {
    List<String> header =
        rows.isEmpty()
            ? List.of()
            : rows.getFirst().stream().map(cell -> cell.toLowerCase(Locale.ROOT)).toList();
    Columns columns =
        new Columns(
            header.indexOf("#domain"),
            header.indexOf("#severity"),
            header.indexOf("#public_comment"));
    if (columns.domain() < 0 || columns.severity() < 0 || rows.size() - 1 > MAX_SERVERS) {
      throw new FederationException(FederationErrorCode.SERVER_LIST_INVALID, MAX_SERVERS);
    }
    return columns;
  }

  private String domain(String raw, List<String> invalid) {
    try {
      return DomainBlocks.normalize(raw, urls.domain());
    } catch (FederationException notADomain) {
      if (invalid.size() < MAX_LISTED_INVALID) {
        invalid.add(raw);
      }
      return null;
    }
  }

  private static ServerBlockSeverity severity(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "suspend" -> ServerBlockSeverity.SUSPEND;
      case "silence" -> ServerBlockSeverity.LIMIT;
      default -> null;
    };
  }

  private static String reason(String comment) {
    return comment.length() > MAX_REASON ? comment.substring(0, MAX_REASON) : comment;
  }

  private static String cell(List<String> row, int at) {
    return at >= 0 && at < row.size() ? row.get(at) : "";
  }
}
