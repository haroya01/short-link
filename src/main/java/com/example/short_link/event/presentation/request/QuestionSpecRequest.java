package com.example.short_link.event.presentation.request;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionSpecRequest(
    @NotBlank String type,
    @NotBlank @Size(max = 200) String label,
    List<String> options,
    boolean required) {

  public QuestionSpec toSpec() {
    return new QuestionSpec(type, label, options, required);
  }
}
