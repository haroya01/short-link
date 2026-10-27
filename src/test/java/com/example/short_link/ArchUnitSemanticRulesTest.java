package com.example.short_link;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.note.application.access.NoteAccess;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.write.PostAccessSettings;
import com.example.short_link.post.application.write.PublishScheduledPostUseCase;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@AnalyzeClasses(
    packages = "com.example.short_link",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchUnitSemanticRulesTest {

  // Existing direct wall-clock reads in domain/application; the number may only go down.
  private static final int WALL_CLOCK_BASELINE = 69;

  // Existing public setters on JPA entities; the number may only go down.
  private static final int ENTITY_SETTER_BASELINE = 9;

  private static final Set<String> OUTBOUND_CLIENTS =
      Set.of(
          "java.net.http.HttpClient",
          "org.apache.hc.client5.http.impl.classic.CloseableHttpClient",
          "org.springframework.web.client.RestClient",
          "com.example.short_link.common.net.HttpFetcher",
          "com.example.short_link.common.net.TxtResolver",
          "software.amazon.awssdk.services.s3.S3Client",
          "org.springframework.mail.javamail.JavaMailSender",
          "com.example.short_link.common.mail.MailSender");

  // Jars are not imported, so Redis types are matched by name rather than by assignability.
  private static final Set<String> REDIS_OPERATION_TYPES =
      Set.of(
          "org.springframework.data.redis.core.RedisOperations",
          "org.springframework.data.redis.core.RedisTemplate",
          "org.springframework.data.redis.core.StringRedisTemplate",
          "org.springframework.data.redis.core.ValueOperations");

  private static final Set<String> REDIS_COUNTER_CALLS =
      Set.of("increment", "decrement", "expire", "expireAt");

  private static final Set<Propagation> OWN_TRANSACTION =
      Set.of(
          Propagation.REQUIRES_NEW,
          Propagation.NOT_SUPPORTED,
          Propagation.NEVER,
          Propagation.NESTED);

  @ArchTest
  static final ArchRule cacheEvictAnnotationIsNotUsed =
      noMethods().should().beAnnotatedWith(CacheEvict.class);

  @ArchTest
  static final ArchRule cacheEntriesAreNotEvictedWithoutWaiting =
      noClasses().should().callMethod(Cache.class, "evict", Object.class);

  @ArchTest
  static final ArchRule proxiedBehaviourIsNotBypassedBySelfInvocation =
      methods().should(keepTheirProxyBehaviourWhenCalledFromTheirOwnClass());

  @ArchTest
  static final ArchRule redisCountersExpireInTheSameScriptAsTheyIncrement =
      classes()
          .that()
          .doNotHaveFullyQualifiedName("com.example.short_link.common.counter.RedisWindowCounter")
          .should(notIncrementOrExpireRedisKeysDirectly());

  @ArchTest
  static final ArchRule postVisibilityIsDecidedByTheAccessPolicy =
      classes()
          .that()
          .resideOutsideOfPackages(
              "..post.domain..",
              "..post.application.access..",
              "..post.application.write..",
              "..post.infrastructure..",
              "..post.scheduler..")
          .and()
          .doNotHaveFullyQualifiedName(
              "com.example.short_link.post.application.read.PostAnalyticsQueryService")
          .should(leaveWhetherAPostIsPublishedToThePolicy());

  @ArchTest
  static final ArchRule noteVisibilityIsAskedOfItsQuestions =
      classes()
          .that(DescribedPredicate.not(JavaClass.Predicates.belongToAnyOf(NoteVisibility.class)))
          .should(leaveWhoSeesANoteToItsVisibility());

  @ArchTest
  static final ArchRule whoMayReadANoteIsAskedOfNoteAccess =
      noClasses()
          .that()
          .doNotBelongToAnyOf(NoteAccess.class)
          .should()
          .callMethod(NoteRepository.class, "visibleTo", Long.class, Collection.class);

  @ArchTest
  static final ArchRule postAudienceIsAskedOfItsQuestions =
      classes()
          .that(DescribedPredicate.not(JavaClass.Predicates.belongToAnyOf(PostAudience.class)))
          .should(leaveWhoReadsAPostToItsAudience());

  @ArchTest
  static final ArchRule whoIsInAPostsRoomIsAskedOfThePolicy =
      noClasses()
          .that()
          .doNotBelongToAnyOf(
              PostAccessPolicy.class, PostAccessSettings.class, PublishScheduledPostUseCase.class)
          .should()
          .callMethod(PostRoomReader.class, "isMember", Long.class, Long.class)
          .orShould()
          .callMethod(PostRoomReader.class, "roomsOf", Long.class)
          .orShould()
          .callMethod(PostRoomReader.class, "membersAmong", Long.class, Collection.class);

  @ArchTest
  static final ArchRule transactionsDoNotCallOutboundClients =
      methods().that(areTransactional()).should(notCallOutboundClients());

  static final ArchRule wallClockInCore =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(LocalDate.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .orShould()
          .callMethod(LocalTime.class, "now")
          .orShould()
          .callMethod(ZonedDateTime.class, "now")
          .orShould()
          .callMethod(OffsetDateTime.class, "now")
          .orShould()
          .callMethod(System.class, "currentTimeMillis");

  static final ArchRule entitySetters =
      noMethods()
          .that()
          .arePublic()
          .and()
          .haveNameMatching("set[A-Z].*")
          .should()
          .beDeclaredInClassesThat()
          .areAnnotatedWith(Entity.class);

  @ArchTest
  static void domainAndApplicationTakeTimeFromTheInjectedClock(JavaClasses classes) {
    assertThat(violations(wallClockInCore, classes))
        .as(
            "Baseline %d — domain/application code should receive the time or use the injected "
                + "Clock. Remove a direct now() call to drop below the baseline, then lower "
                + "WALL_CLOCK_BASELINE.",
            WALL_CLOCK_BASELINE)
        .hasSizeLessThanOrEqualTo(WALL_CLOCK_BASELINE);
  }

  @ArchTest
  static void entitiesChangeThroughNamedOperations(JavaClasses classes) {
    assertThat(violations(entitySetters, classes))
        .as(
            "Baseline %d — change entity state through a named operation that keeps its rules. "
                + "Replace a setter to drop below the baseline, then lower ENTITY_SETTER_BASELINE.",
            ENTITY_SETTER_BASELINE)
        .hasSizeLessThanOrEqualTo(ENTITY_SETTER_BASELINE);
  }

  private static ArchCondition<JavaClass> leaveWhetherAPostIsPublishedToThePolicy() {
    return new ArchCondition<>("leave whether a post is published to the access policy") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
          AccessTarget target = access.getTarget();
          boolean publishedCheck =
              target.getOwner().isEquivalentTo(PostEntity.class)
                      && target.getName().equals("isPublished")
                  || target.getOwner().isEquivalentTo(PostStatus.class)
                      && target.getName().equals("PUBLISHED");
          if (publishedCheck) {
            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> leaveWhoSeesANoteToItsVisibility() {
    return new ArchCondition<>("leave who sees a note to the questions NoteVisibility answers") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
          AccessTarget target = access.getTarget();
          if (!target.getOwner().isEquivalentTo(NoteVisibility.class)) continue;
          boolean named =
              NoteVisibility.where(visibility -> true).stream()
                  .anyMatch(visibility -> visibility.name().equals(target.getName()));
          if (named || target.getName().equals("ordinal")) {
            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> leaveWhoReadsAPostToItsAudience() {
    return new ArchCondition<>("leave who reads a post to the questions PostAudience answers") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
          AccessTarget target = access.getTarget();
          if (!target.getOwner().isEquivalentTo(PostAudience.class)) continue;
          boolean named =
              PostAudience.where(audience -> true).stream()
                  .anyMatch(audience -> audience.name().equals(target.getName()));
          if (named || target.getName().equals("ordinal")) {
            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
          }
        }
      }
    };
  }

  private static List<String> violations(ArchRule rule, JavaClasses classes) {
    return rule.evaluate(classes).getFailureReport().getDetails();
  }

  private static ArchCondition<JavaMethod> keepTheirProxyBehaviourWhenCalledFromTheirOwnClass() {
    return new ArchCondition<>("keep their proxy behaviour when called from their own class") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Optional<String> effect = proxyEffect(method);
        if (effect.isEmpty()) return;
        for (JavaMethodCall call : method.getCallsOfSelf()) {
          if (!call.getOriginOwner().equals(method.getOwner())) continue;
          if (effect.get().equals("transaction") && isTransactional(call.getOrigin())) continue;
          events.add(
              SimpleConditionEvent.violated(
                  call, call.getDescription() + " bypasses " + effect.get()));
        }
      }
    };
  }

  private static Optional<String> proxyEffect(JavaMethod method) {
    if (method.isAnnotatedWith(Async.class)) return Optional.of("@Async");
    if (method.isAnnotatedWith(Cacheable.class)
        || method.isAnnotatedWith(CachePut.class)
        || method.isAnnotatedWith(CacheEvict.class)) {
      return Optional.of("caching");
    }
    Optional<Transactional> transactional = method.tryGetAnnotationOfType(Transactional.class);
    if (transactional.isEmpty()) return Optional.empty();
    Propagation propagation = transactional.get().propagation();
    return Optional.of(
        OWN_TRANSACTION.contains(propagation)
            ? "@Transactional(propagation = " + propagation + ")"
            : "transaction");
  }

  private static boolean isTransactional(JavaCodeUnit codeUnit) {
    return codeUnit.isAnnotatedWith(Transactional.class)
        || codeUnit.getOwner().isAnnotatedWith(Transactional.class);
  }

  private static DescribedPredicate<JavaMethod> areTransactional() {
    return DescribedPredicate.describe("are transactional", method -> isTransactional(method));
  }

  private static ArchCondition<JavaMethod> notCallOutboundClients() {
    return new ArchCondition<>("not call outbound network clients") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
          if (OUTBOUND_CLIENTS.contains(call.getTargetOwner().getName())) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notIncrementOrExpireRedisKeysDirectly() {
    return new ArchCondition<>("not increment or expire Redis keys outside RedisWindowCounter") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
          boolean redis = REDIS_OPERATION_TYPES.contains(call.getTargetOwner().getName());
          if (redis && REDIS_COUNTER_CALLS.contains(call.getName())) {
            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
          }
        }
      }
    };
  }
}
