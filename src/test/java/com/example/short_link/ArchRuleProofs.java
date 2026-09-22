package com.example.short_link;

import archfixtures.CacheFixtures;
import archfixtures.EntityFixtures;
import archfixtures.RedisFixtures;
import archfixtures.TransactionFixtures;
import archfixtures.domain.ClockFixtures;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;

/** 규칙마다 잡아야 할 예제와 잡으면 안 되는 예제. 규칙이 아무것도 잡지 못하게 고장 나도 CI 는 초록이라, 규칙이 둘을 구분한다는 것을 매번 다시 확인한다. */
final class ArchRuleProofs {

  /** 규칙 하나와, 그 규칙이 잡아야 할 클래스들·남겨둬야 할 클래스들. */
  record Proof(String rule, ArchRule check, List<Class<?>> flagged, List<Class<?>> clean) {}

  static final List<Proof> PROOFS =
      List.of(
          new Proof(
              "ArchUnitSemanticRulesTest.cacheEvictAnnotationIsNotUsed",
              ArchUnitSemanticRulesTest.cacheEvictAnnotationIsNotUsed,
              List.of(CacheFixtures.EvictsByAnnotation.class),
              List.of(CacheFixtures.EvictsDirectly.class, CacheFixtures.EvictsIfPresent.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.cacheEntriesAreNotEvictedWithoutWaiting",
              ArchUnitSemanticRulesTest.cacheEntriesAreNotEvictedWithoutWaiting,
              List.of(CacheFixtures.EvictsDirectly.class),
              List.of(CacheFixtures.EvictsIfPresent.class, CacheFixtures.EvictsByAnnotation.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.proxiedBehaviourIsNotBypassedBySelfInvocation",
              ArchUnitSemanticRulesTest.proxiedBehaviourIsNotBypassedBySelfInvocation,
              List.of(
                  TransactionFixtures.RequiresNewCalledFromItsOwnClass.class,
                  TransactionFixtures.TransactionalCalledFromPlainMethod.class),
              List.of(TransactionFixtures.TransactionalCalledFromTransactionalMethod.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.redisCountersExpireInTheSameScriptAsTheyIncrement",
              ArchUnitSemanticRulesTest.redisCountersExpireInTheSameScriptAsTheyIncrement,
              List.of(RedisFixtures.IncrementsThenExpires.class),
              List.of(RedisFixtures.RunsOneScript.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.transactionsDoNotCallOutboundClients",
              ArchUnitSemanticRulesTest.transactionsDoNotCallOutboundClients,
              List.of(TransactionFixtures.CallsNetworkInsideTransaction.class),
              List.of(TransactionFixtures.CallsNetworkOutsideTransaction.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.wallClockInCore",
              ArchUnitSemanticRulesTest.wallClockInCore,
              List.of(ClockFixtures.ReadsWallClockDirectly.class),
              List.of(ClockFixtures.TakesTimeFromTheClock.class)),
          new Proof(
              "ArchUnitSemanticRulesTest.entitySetters",
              ArchUnitSemanticRulesTest.entitySetters,
              List.of(EntityFixtures.EntityWithPublicSetter.class),
              List.of(EntityFixtures.EntityWithNamedOperation.class)));

  /** 아직 반례로 증명하지 못한 규칙. 새 규칙은 여기 추가할 수 없고, 이 목록은 줄어들기만 한다. */
  static final Set<String> RULES_WITHOUT_A_PROOF =
      Set.of(
          "ArchUnitGraphRulesTest.domainDoesNotDependOnPresentation",
          "ArchUnitGraphRulesTest.domainDoesNotDependOnApplication",
          "ArchUnitGraphRulesTest.domainDoesNotDependOnInfrastructure",
          "ArchUnitGraphRulesTest.applicationDoesNotDependOnPresentation",
          "ArchUnitGraphRulesTest.awsSdkConfinedToCommonStorage",
          "ArchUnitGraphRulesTest.maxmindSdkConfinedToCommonGeoip",
          "ArchUnitGraphRulesTest.jjwtSdkConfinedToUserApplication",
          "ArchUnitGraphRulesTest.jsoupSdkConfinedToOgScraper",
          "ArchUnitGraphRulesTest.burstHeuristicConfinedToBotClassifier",
          "ArchUnitGraphRulesTest.yauaaSdkConfinedToUserAgentClassifier",
          "ArchUnitGraphRulesTest.zxingSdkConfinedToQrPngEncoder",
          "ArchUnitGraphRulesTest.useCasesLiveInApplicationWrite",
          "ArchUnitGraphRulesTest.apacheHttpClientConfined",
          "ArchUnitGraphRulesTest.springDataNotInApplication",
          "ArchUnitGraphRulesTest.springDataNotInDomain",
          "ArchUnitGraphRulesTest.contextsAreFreeOfCycles",
          "ArchUnitGraphRulesTest.queryServicesLiveInApplicationRead",
          "ArchUnitGraphRulesTest.controllersLiveInPresentation",
          "ArchUnitGraphRulesTest.transactionalNotInPresentationOrDomain",
          "ArchUnitGraphRulesTest.propertiesAreRecords",
          "RepositoryUnusedMethodTest.no_unused_repository_methods",
          "RepositoryUnusedMethodTest.no_unused_service_or_usecase_methods");

  /** 위 목록이 처음 만들어졌을 때의 이름. 하나를 증명하고 다른 하나를 슬쩍 넣으면 크기는 같아도 이 집합 밖이라 걸린다. */
  static final Set<String> RULES_WITHOUT_A_PROOF_BASELINE =
      Set.of(
          "ArchUnitGraphRulesTest.domainDoesNotDependOnPresentation",
          "ArchUnitGraphRulesTest.domainDoesNotDependOnApplication",
          "ArchUnitGraphRulesTest.domainDoesNotDependOnInfrastructure",
          "ArchUnitGraphRulesTest.applicationDoesNotDependOnPresentation",
          "ArchUnitGraphRulesTest.awsSdkConfinedToCommonStorage",
          "ArchUnitGraphRulesTest.maxmindSdkConfinedToCommonGeoip",
          "ArchUnitGraphRulesTest.jjwtSdkConfinedToUserApplication",
          "ArchUnitGraphRulesTest.jsoupSdkConfinedToOgScraper",
          "ArchUnitGraphRulesTest.burstHeuristicConfinedToBotClassifier",
          "ArchUnitGraphRulesTest.yauaaSdkConfinedToUserAgentClassifier",
          "ArchUnitGraphRulesTest.zxingSdkConfinedToQrPngEncoder",
          "ArchUnitGraphRulesTest.useCasesLiveInApplicationWrite",
          "ArchUnitGraphRulesTest.apacheHttpClientConfined",
          "ArchUnitGraphRulesTest.springDataNotInApplication",
          "ArchUnitGraphRulesTest.springDataNotInDomain",
          "ArchUnitGraphRulesTest.contextsAreFreeOfCycles",
          "ArchUnitGraphRulesTest.queryServicesLiveInApplicationRead",
          "ArchUnitGraphRulesTest.controllersLiveInPresentation",
          "ArchUnitGraphRulesTest.transactionalNotInPresentationOrDomain",
          "ArchUnitGraphRulesTest.propertiesAreRecords",
          "RepositoryUnusedMethodTest.no_unused_repository_methods",
          "RepositoryUnusedMethodTest.no_unused_service_or_usecase_methods");

  private ArchRuleProofs() {}
}
