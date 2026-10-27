package com.example.short_link;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// SQL strings name tables directly, so a module can read another module's schema without
// importing a single class of it. Each line of the baseline is one file reading one foreign table.
class CrossModuleSqlRatchetTest {

  static final int CEILING = 150;

  private static final Path MAIN = Path.of("src/main/java");
  private static final Path MODULES = MAIN.resolve("com/example/short_link");
  private static final Path JAVA_MIGRATIONS = MAIN.resolve("db/migration");
  private static final Path SQL_MIGRATIONS = Path.of("src/main/resources/db/migration");
  private static final Path BASELINE = Path.of("src/test/resources/cross-module-sql.txt");
  private static final String MODULE_ROOT = "com/example/short_link/";

  private static final Map<String, String> OWNERS_WITHOUT_AN_ENTITY =
      Map.ofEntries(
          entry("author_post_number", "post"),
          entry("click_daily_dim", "link"),
          entry("click_daily_visitors", "link"),
          entry("federation_relayed_note", "federation"),
          entry("follow_suggestion_dismissal", "user"),
          entry("image_variant", "common"),
          entry("link_profile_binding", "link"),
          entry("local_seen", "federation"),
          entry("note_conversation_mute", "note"),
          entry("note_feed_preference", "note"),
          entry("note_list_member", "note"),
          entry("note_poll_remote_vote", "note"),
          entry("note_poll_vote", "note"),
          entry("note_recipient", "note"),
          entry("note_repost_mute", "note"),
          entry("note_tag", "note"),
          entry("notification_permission", "notification"),
          entry("notification_policy", "notification"),
          entry("post_note_quote", "post"),
          entry("post_read_milestone", "post"),
          entry("post_tag", "post"),
          entry("remote_dwell", "federation"),
          entry("remote_note_reach", "federation"),
          entry("remote_seen", "federation"),
          entry("room_invitation", "room"),
          entry("room_join_request", "room"),
          entry("room_member", "room"),
          entry("room_note_erasure", "note"),
          entry("room_post_erasure", "post"),
          entry("room_removal", "room"),
          entry("room_suggestion_dismissal", "room"));

  private static final Pattern TABLE_REFERENCE =
      Pattern.compile(
          "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+`?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-z_0-9]+)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ENTITY = Pattern.compile("^@Entity\\b", Pattern.MULTILINE);
  private static final Pattern SPLICE =
      Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern TABLE_NAME =
      Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([a-z_0-9]+)\"");

  record Schema(
      Set<String> tables, Map<String, String> ownerByTable, Map<String, String> tableByEntity) {}

  @Test
  void sqlReachesAnotherModulesTablesOnlyWhereItAlreadyDid() throws IOException {
    Set<String> current = new TreeSet<>(crossModuleReferences(schema(), sources()));
    Set<String> baseline = new TreeSet<>(Files.readAllLines(BASELINE, StandardCharsets.UTF_8));

    Set<String> added = new TreeSet<>(current);
    added.removeAll(baseline);
    Set<String> gone = new TreeSet<>(baseline);
    gone.removeAll(current);

    assertThat(added)
        .as(
            "SQL here reads another module's table. Ask the owning module for a read API instead;"
                + " a consumer-only report may add the line to %s and raise CEILING in the same PR.",
            BASELINE)
        .isEmpty();
    assertThat(gone)
        .as("These reads are gone. Delete the lines from %s and lower CEILING.", BASELINE)
        .isEmpty();
    assertThat(current).as("CEILING is the number of lines in %s", BASELINE).hasSize(CEILING);
  }

  @Test
  void everyTableHasAnOwningModule() throws IOException {
    Schema schema = schema();

    Set<String> unowned = new TreeSet<>(schema.tables());
    unowned.removeAll(schema.ownerByTable().keySet());
    assertThat(unowned)
        .as("A table without an entity names its owner in OWNERS_WITHOUT_AN_ENTITY")
        .isEmpty();

    Set<String> listedButMapped = new TreeSet<>(OWNERS_WITHOUT_AN_ENTITY.keySet());
    listedButMapped.retainAll(entityTables(sources()).keySet());
    assertThat(listedButMapped)
        .as("These tables have an entity now; drop them from the list")
        .isEmpty();

    Set<String> listedButMissing = new TreeSet<>(OWNERS_WITHOUT_AN_ENTITY.keySet());
    listedButMissing.removeAll(schema.tables());
    assertThat(listedButMissing).as("No migration creates these tables").isEmpty();
  }

  @Test
  void theScannerCatchesAnotherModulesTableAndLeavesItsOwnAlone() {
    Schema schema =
        new Schema(
            Set.of(
                "posts",
                "note",
                "users",
                "room",
                "federation_follower",
                "user_block",
                "user_mute",
                "room_member"),
            Map.of(
                "posts", "post",
                "note", "note",
                "users", "user",
                "room", "room",
                "federation_follower", "federation",
                "user_block", "user",
                "user_mute", "user",
                "room_member", "room"),
            Map.of("RoomEntity", "room"));
    String post = MODULE_ROOT + "post/infrastructure/PostSql.java";
    String fragment = MODULE_ROOT + "common/user/HeardSql.java";
    Map<String, String> sources =
        Map.of(
            post,
            """
            class PostSql {
              String joined = "SELECT p.id FROM posts p JOIN note n ON n.id = p.note_id";
              String jpql = "select r from RoomEntity r";
              String split = "SELECT 1 FROM" + " users u WHERE u.id = :id";
              String table = "federation_follower";
              String spliced = "SELECT COUNT(*) FROM " + table + " r";
              String heard = "WHERE NOT " + HeardSql.memberSilenced(":viewer", "p.user_id");
              String own = "UPDATE posts SET view_count = view_count + 1";
              String prose = "a reply from another server";
            }
            """,
            fragment,
            """
            final class HeardSql {
              static String memberSilenced(String viewer, String author) {
                return "EXISTS (SELECT 1 FROM user_block b WHERE b.blocker_id = " + viewer + ")"
                    + muted(viewer);
              }

              static String muted(String viewer) {
                return " OR EXISTS (SELECT 1 FROM user_mute m WHERE m.user_id = " + viewer + ")";
              }

              static String inRoom() {
                return "SELECT 1 FROM room_member";
              }
            }
            """,
            MODULE_ROOT + "note/infrastructure/NoteSql.java",
            """
            class NoteSql {
              String own = "DELETE FROM note WHERE id = :id";
            }
            """,
            MODULE_ROOT + "link/application/LinkMetrics.java",
            """
            class LinkMetrics {
              void blocked(MeterRegistry meters) {
                meters.counter("short_link.created", "result", "user_block").increment();
              }
            }
            """);

    assertThat(crossModuleReferences(schema, sources))
        .containsExactly(
            "common -> room\t" + fragment + "\troom_member",
            "common -> user\t" + fragment + "\tuser_block",
            "common -> user\t" + fragment + "\tuser_mute",
            "post -> federation\t" + post + "\tfederation_follower",
            "post -> note\t" + post + "\tnote",
            "post -> room\t" + post + "\troom",
            "post -> user\t" + post + "\tusers");
  }

  static List<String> crossModuleReferences(Schema schema, Map<String, String> sources) {
    Set<String> found = new TreeSet<>();
    sources.forEach(
        (path, source) -> {
          Parsed file = parse(path, source);
          for (String table : tablesIn(schema, file)) {
            String owner = schema.ownerByTable().get(table);
            if (owner != null && !owner.equals(file.module())) {
              found.add(file.module() + " -> " + owner + "\t" + file.path() + "\t" + table);
            }
          }
        });
    return new ArrayList<>(found);
  }

  record Literal(int start, int end, String text) {}

  record Parsed(String path, String module, List<Literal> literals, boolean splicesTableNames) {}

  private static Set<String> tablesIn(Schema schema, Parsed file) {
    Set<String> tables = new TreeSet<>();
    for (Literal literal : file.literals()) {
      if (file.splicesTableNames()
          && literal.text().indexOf('_') > 0
          && schema.tables().contains(literal.text())) {
        tables.add(literal.text());
      }
      Matcher reference = TABLE_REFERENCE.matcher(literal.text());
      while (reference.find()) {
        String name = reference.group(1);
        String table =
            schema.tables().contains(name.toLowerCase())
                ? name.toLowerCase()
                : schema.tableByEntity().get(name);
        if (table != null) {
          tables.add(table);
        }
      }
    }
    return tables;
  }

  private static Parsed parse(String path, String source) {
    StringBuilder code = new StringBuilder(source.length());
    List<Literal> pieces = new ArrayList<>();
    int n = source.length();
    int i = 0;
    while (i < n) {
      char c = source.charAt(i);
      int end;
      if (source.startsWith("//", i)) {
        end = source.indexOf('\n', i) < 0 ? n : source.indexOf('\n', i);
      } else if (source.startsWith("/*", i)) {
        end = source.indexOf("*/", i + 2) < 0 ? n : source.indexOf("*/", i + 2) + 2;
      } else if (source.startsWith("\"\"\"", i)) {
        int j = i + 3;
        while (j < n && !source.startsWith("\"\"\"", j)) {
          j += source.charAt(j) == '\\' ? 2 : 1;
        }
        end = Math.min(n, j + 3);
        pieces.add(new Literal(i, end, source.substring(i + 3, Math.min(j, n))));
      } else if (c == '"' || c == '\'') {
        int j = i + 1;
        while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
          j += source.charAt(j) == '\\' ? 2 : 1;
        }
        end = Math.min(n, j + 1);
        if (c == '"') {
          pieces.add(new Literal(i, end, source.substring(i + 1, Math.min(j, n))));
        }
      } else {
        code.append(c);
        i++;
        continue;
      }
      for (int k = i; k < end; k++) {
        code.append(source.charAt(k) == '\n' ? '\n' : ' ');
      }
      i = end;
    }
    String masked = code.toString();
    List<Literal> literals = new ArrayList<>();
    for (Literal piece : pieces) {
      Literal last = literals.isEmpty() ? null : literals.getLast();
      if (last != null && masked.substring(last.end(), piece.start()).matches("[\\s+]*")) {
        literals.set(
            literals.size() - 1,
            new Literal(last.start(), piece.end(), last.text() + piece.text()));
      } else {
        literals.add(piece);
      }
    }
    return new Parsed(
        path,
        module(path),
        literals,
        literals.stream().anyMatch(literal -> SPLICE.matcher(literal.text()).find()));
  }

  private static String module(String path) {
    String rest = path.substring(path.indexOf(MODULE_ROOT) + MODULE_ROOT.length());
    int slash = rest.indexOf('/');
    return slash < 0 ? "" : rest.substring(0, slash);
  }

  private static Schema schema() throws IOException {
    Set<String> tables = new TreeSet<>();
    for (Path migration : files(SQL_MIGRATIONS, ".sql")) {
      collectCreatedTables(Files.readString(migration, StandardCharsets.UTF_8), tables);
    }
    for (Path migration : files(JAVA_MIGRATIONS, ".java")) {
      collectCreatedTables(Files.readString(migration, StandardCharsets.UTF_8), tables);
    }
    Map<String, String> sources = sources();
    Map<String, String> ownerByTable = new HashMap<>(OWNERS_WITHOUT_AN_ENTITY);
    Map<String, String> tableByEntity = new HashMap<>();
    entityTables(sources)
        .forEach(
            (table, path) -> {
              ownerByTable.put(table, module(path));
              String file = path.substring(path.lastIndexOf('/') + 1);
              tableByEntity.put(file.substring(0, file.length() - ".java".length()), table);
            });
    return new Schema(tables, ownerByTable, tableByEntity);
  }

  private static Map<String, String> entityTables(Map<String, String> sources) {
    Map<String, String> pathByTable = new TreeMap<>();
    sources.forEach(
        (path, source) -> {
          Matcher table = TABLE_NAME.matcher(source);
          if (ENTITY.matcher(source).find() && table.find()) {
            pathByTable.put(table.group(1), path);
          }
        });
    return pathByTable;
  }

  private static void collectCreatedTables(String ddl, Set<String> tables) {
    Matcher created = CREATE_TABLE.matcher(ddl);
    while (created.find()) {
      tables.add(created.group(1).toLowerCase());
    }
  }

  private static Map<String, String> sources() throws IOException {
    Map<String, String> sources = new TreeMap<>();
    for (Path file : files(MODULES, ".java")) {
      sources.put(MAIN.relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8));
    }
    return sources;
  }

  private static List<Path> files(Path root, String suffix) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
    }
  }
}
