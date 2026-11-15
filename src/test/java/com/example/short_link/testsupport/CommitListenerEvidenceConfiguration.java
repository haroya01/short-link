package com.example.short_link.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalApplicationListener;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@Profile("test")
public class CommitListenerEvidenceConfiguration {

  static final Path DIRECTORY = Path.of("build/reports/commit-listeners");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Pattern LISTENER_ID = Pattern.compile("^(.+)\\.([^.(]+)\\((.*)\\)$");
  private static final String APPLICATION = "com.example.short_link";
  private static final Map<String, AtomicInteger> DELIVERIES = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> DROPS = new ConcurrentHashMap<>();

  private final List<Watched> watched = new CopyOnWriteArrayList<>();

  static int deliveries(String listener) {
    return count(DELIVERIES, listener).get();
  }

  static int drops(String listener) {
    return count(DROPS, listener).get();
  }

  @EventListener
  public void watchCommitListeners(ContextRefreshedEvent refreshed) {
    if (!(refreshed.getApplicationContext() instanceof AbstractApplicationContext context)) {
      return;
    }
    for (ApplicationListener<?> listener : context.getApplicationListeners()) {
      if (!(listener instanceof TransactionalApplicationListener<?> transactional)
          || transactional.getTransactionPhase() != TransactionPhase.AFTER_COMMIT) {
        continue;
      }
      Watched entry = Watched.of(transactional);
      if (entry == null) {
        continue;
      }
      watched.add(entry);
      write(
          DIRECTORY.resolve("inventory").resolve(entry.name() + ".json"),
          Map.of("listener", entry.name(), "fallbackExecution", entry.fallbackExecution()));
      transactional.addCallback(
          new TransactionalApplicationListener.SynchronizationCallback() {
            @Override
            public void postProcessEvent(ApplicationEvent event, Throwable failure) {
              if (count(DELIVERIES, entry.name()).getAndIncrement() == 0) {
                write(
                    DIRECTORY.resolve("delivered").resolve(entry.name() + ".json"),
                    Map.of("listener", entry.name()));
              }
            }
          });
    }
  }

  @EventListener
  public void recordPublishedOutsideTransaction(Object event) {
    if (watched.isEmpty() || insideTransaction()) {
      return;
    }
    ApplicationEvent wrapped =
        event instanceof ApplicationEvent applicationEvent
            ? applicationEvent
            : new PayloadApplicationEvent<>(this, event);
    ResolvableType type = ResolvableType.forInstance(wrapped);
    for (Watched entry : watched) {
      if (entry.fallbackExecution() || !entry.listens(type)) {
        continue;
      }
      Publisher publisher = Publisher.nearest();
      count(DROPS, entry.name()).incrementAndGet();
      write(
          DIRECTORY.resolve("dropped").resolve(entry.name() + "__" + publisher.name() + ".json"),
          Map.of(
              "listener", entry.name(),
              "event", event.getClass().getSimpleName(),
              "publisher", publisher.name(),
              "production", publisher.production()));
    }
  }

  static boolean fromMainClasses(String className) {
    try {
      CodeSource source = Class.forName(className).getProtectionDomain().getCodeSource();
      URL location = source == null ? null : source.getLocation();
      return location != null && location.getPath().contains("/classes/java/main");
    } catch (ClassNotFoundException | LinkageError unknown) {
      return false;
    }
  }

  private static boolean insideTransaction() {
    return TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive();
  }

  private static AtomicInteger count(Map<String, AtomicInteger> counts, String listener) {
    return counts.computeIfAbsent(listener, unused -> new AtomicInteger());
  }

  private static void write(Path destination, Object value) {
    try {
      Files.createDirectories(destination.getParent());
      Path staged =
          destination.resolveSibling(
              destination.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
      Files.writeString(staged, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
      Files.move(
          staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private record Publisher(String name, boolean production) {

    static Publisher nearest() {
      for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
        String className = frame.getClassName();
        if (!className.startsWith(APPLICATION)
            || className.contains("$$")
            || className.startsWith(CommitListenerEvidenceConfiguration.class.getName())) {
          continue;
        }
        return new Publisher(
            className.substring(className.lastIndexOf('.') + 1) + "." + frame.getMethodName(),
            fromMainClasses(className));
      }
      return new Publisher("unknown", false);
    }
  }

  private record Watched(
      String name, boolean fallbackExecution, TransactionalApplicationListener<?> listener) {

    static Watched of(TransactionalApplicationListener<?> listener) {
      Matcher id = LISTENER_ID.matcher(listener.getListenerId());
      if (!id.matches() || !fromMainClasses(id.group(1))) {
        return null;
      }
      String owner = id.group(1);
      String method = id.group(2);
      return new Watched(
          owner.substring(owner.lastIndexOf('.') + 1) + "." + method,
          fallbackExecution(owner, method, id.group(3)),
          listener);
    }

    boolean listens(ResolvableType eventType) {
      return listener instanceof GenericApplicationListener generic
          && generic.supportsEventType(eventType);
    }

    private static boolean fallbackExecution(String owner, String method, String parameters) {
      try {
        for (Method candidate : Class.forName(owner).getDeclaredMethods()) {
          if (candidate.getName().equals(method) && parameterNames(candidate).equals(parameters)) {
            TransactionalEventListener annotation =
                AnnotatedElementUtils.findMergedAnnotation(
                    candidate, TransactionalEventListener.class);
            return annotation != null && annotation.fallbackExecution();
          }
        }
      } catch (ClassNotFoundException | LinkageError unknown) {
        return false;
      }
      return false;
    }

    private static String parameterNames(Method method) {
      StringBuilder names = new StringBuilder();
      for (Class<?> parameter : method.getParameterTypes()) {
        if (names.length() > 0) {
          names.append(',');
        }
        names.append(parameter.getName());
      }
      return names.toString();
    }
  }
}
