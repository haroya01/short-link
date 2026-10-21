package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// The image build trains its CDS archive with the Dockerfile's flags and no database or Redis to
// reach; a bean that connects while the context refreshes would fail the deploy build, which CI
// never runs. This runs the same refresh on the main class path (the test one adds JSqlParser,
// which changes how native queries are read) against ports where nothing listens.
class CdsTrainingTest {

  private static final Pattern TRAINING =
      Pattern.compile("RUN java -XX:ArchiveClassesAtExit=\\S+(.*?)-jar app\\.jar", Pattern.DOTALL);
  private static final Pattern FLAG = Pattern.compile("(-D\\S+|-Xlog:\\S+)");

  @Test
  void theContextRefreshesWithTheDockerfilesTrainingFlagsAndNothingToConnectTo()
      throws IOException, InterruptedException {
    Matcher training = TRAINING.matcher(Files.readString(Path.of("Dockerfile")));
    assertThat(training.find()).as("Dockerfile trains a CDS archive").isTrue();
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    Matcher flag = FLAG.matcher(training.group(1));
    while (flag.find()) {
      command.add(
          flag.group(1)
              .replace("localhost:3306", "127.0.0.1:9")
              .replace("REDIS_HOST=localhost", "REDIS_HOST=127.0.0.1"));
    }
    command.add("-DREDIS_PORT=9");
    command.add("-cp");
    command.add(System.getProperty("short-link.main-classpath"));
    command.add(ShortLinkApplication.class.getName());
    Path log = Files.createTempFile("cds-training", ".log");
    Process process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();

    boolean exited = process.waitFor(3, TimeUnit.MINUTES);
    if (!exited) {
      process.destroyForcibly();
    }

    assertThat(exited && process.exitValue() == 0)
        .as("training run exited 0 within 3 minutes:%n%s", tail(log))
        .isTrue();
  }

  private static String tail(Path log) throws IOException {
    List<String> lines = Files.readAllLines(log);
    return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
  }
}
