package com.housinglab.api.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExplanationControllerTest {
  private final ExplanationController controller = new ExplanationController();

  @Test
  void explainsOnlySuppliedCalculationEvidence() {
    var response = controller.explain(new ExplanationController.Request(
        "상계보람", 540_000_000L, 645_000_000L, 633_000_000L,
        699_632_232L, 42_700_000L, 4, "HIGH", "housing-cashflow-1.0.0"));

    assertThat(response.headline()).contains("전세 + 투자");
    assertThat(response.summary()).contains("+19.4%").contains("6663.2만원");
    assertThat(response.evidence()).hasSize(4);
    assertThat(response.explanationVersion()).isEqualTo("evidence-explanation-1.0.0");
  }
}
