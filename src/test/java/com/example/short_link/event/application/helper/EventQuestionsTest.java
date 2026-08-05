package com.example.short_link.event.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.QuestionType;
import com.example.short_link.event.exception.EventException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EventQuestionsTest {

  private EventQuestionEntity question(
      long id, QuestionType type, String options, boolean required) {
    EventQuestionEntity entity = new EventQuestionEntity(1L, 0, type, "Q" + id, options, required);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  @Test
  void validateSpecs_capsAtTen() {
    List<QuestionSpec> ten =
        java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> new QuestionSpec("SHORT_TEXT", "q" + i, null, false))
            .toList();
    EventQuestions.validateSpecs(ten);

    List<QuestionSpec> eleven =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(i -> new QuestionSpec("SHORT_TEXT", "q" + i, null, false))
            .toList();
    assertThatThrownBy(() -> EventQuestions.validateSpecs(eleven))
        .isInstanceOf(EventException.class);
  }

  @Test
  void singleChoice_requiresTwoToTenOptions() {
    assertThatThrownBy(
            () ->
                EventQuestions.validateSpecs(
                    List.of(new QuestionSpec("SINGLE_CHOICE", "메뉴", List.of("한식"), false))))
        .isInstanceOf(EventException.class);
  }

  @Test
  void requiredQuestion_mustBeAnswered() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SHORT_TEXT, null, true));
    assertThatThrownBy(() -> EventQuestions.validateAndSerializeAnswers(questions, Map.of()))
        .isInstanceOf(EventException.class);
  }

  @Test
  void singleChoiceAnswer_mustBeAnOption() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SINGLE_CHOICE, "[\"a\",\"b\"]", true));
    assertThatThrownBy(
            () -> EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "c")))
        .isInstanceOf(EventException.class);
    String json = EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "a"));
    assertThat(EventQuestions.deserializeAnswers(json)).containsEntry("10", "a");
  }

  @Test
  void optionalUnanswered_isOmitted() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SHORT_TEXT, null, false));
    assertThat(EventQuestions.validateAndSerializeAnswers(questions, Map.of())).isNull();
  }

  @Test
  void unknownType_isRejected() {
    assertThatThrownBy(() -> EventQuestions.parseType("ESSAY")).isInstanceOf(EventException.class);
    assertThatThrownBy(() -> EventQuestions.parseType(null)).isInstanceOf(EventException.class);
    assertThat(EventQuestions.parseType("short_text")).isEqualTo(QuestionType.SHORT_TEXT);
  }

  @Test
  void label_mustBePresentAndBounded() {
    for (String label : new String[] {null, "  ", "가".repeat(201)}) {
      assertThatThrownBy(
              () ->
                  EventQuestions.validateSpecs(
                      List.of(new QuestionSpec("SHORT_TEXT", label, null, false))))
          .isInstanceOf(EventException.class);
    }
  }

  @Test
  void singleChoiceOption_mustBePresentAndBounded() {
    assertThatThrownBy(
            () ->
                EventQuestions.validateSpecs(
                    List.of(new QuestionSpec("SINGLE_CHOICE", "메뉴", List.of("한식", "  "), false))))
        .isInstanceOf(EventException.class);
    assertThatThrownBy(
            () ->
                EventQuestions.validateSpecs(
                    List.of(
                        new QuestionSpec(
                            "SINGLE_CHOICE", "메뉴", List.of("한식", "가".repeat(101)), false))))
        .isInstanceOf(EventException.class);
    assertThatThrownBy(
            () ->
                EventQuestions.validateSpecs(
                    List.of(
                        new QuestionSpec(
                            "SINGLE_CHOICE",
                            "메뉴",
                            java.util.stream.IntStream.range(0, 11)
                                .mapToObj(i -> "옵션" + i)
                                .toList(),
                            false))))
        .isInstanceOf(EventException.class);
  }

  @Test
  void nullSpecs_passValidation() {
    EventQuestions.validateSpecs(null);
    EventQuestions.validateSpecs(List.of());
  }

  @Test
  void options_roundTripThroughJson() {
    assertThat(EventQuestions.serializeOptions(null)).isNull();
    assertThat(EventQuestions.serializeOptions(List.of())).isNull();
    String json = EventQuestions.serializeOptions(List.of("한식", "양식"));
    assertThat(EventQuestions.deserializeOptions(json)).containsExactly("한식", "양식");
    assertThat(EventQuestions.deserializeOptions(null)).isEmpty();
    assertThat(EventQuestions.deserializeOptions("not-json")).isEmpty();
  }

  @Test
  void brokenAnswersJson_readsAsEmpty() {
    assertThat(EventQuestions.deserializeAnswers(null)).isEmpty();
    assertThat(EventQuestions.deserializeAnswers("not-json")).isEmpty();
  }

  @Test
  void answer_isTrimmedAndCapped() {
    List<EventQuestionEntity> questions =
        List.of(question(10L, QuestionType.SHORT_TEXT, null, true));
    assertThatThrownBy(
            () ->
                EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "가".repeat(501))))
        .isInstanceOf(EventException.class);
    String json = EventQuestions.validateAndSerializeAnswers(questions, Map.of(10L, "  답  "));
    assertThat(EventQuestions.deserializeAnswers(json)).containsEntry("10", "답");
  }
}
