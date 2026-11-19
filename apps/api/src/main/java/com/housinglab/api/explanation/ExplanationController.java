package com.housinglab.api.explanation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explanations")
public class ExplanationController {
  public record Request(
      @NotBlank String buildingName,
      @Positive long historicalPriceKrw,
      @Positive long currentPriceKrw,
      long homeNetWorthKrw,
      @PositiveOrZero long alternativeNetWorthKrw,
      @PositiveOrZero long totalInterestKrw,
      @PositiveOrZero int observedTradeCount,
      @NotBlank String confidence,
      @NotBlank String calculationVersion) {}

  public record Evidence(String label, String value, String interpretation) {}

  public record Response(
      String headline,
      String summary,
      List<Evidence> evidence,
      List<String> caveats,
      String explanationVersion) {}

  @PostMapping
  Response explain(@Valid @RequestBody Request request) {
    long difference = request.homeNetWorthKrw() - request.alternativeNetWorthKrw();
    var priceChange = BigDecimal.valueOf(request.currentPriceKrw() - request.historicalPriceKrw())
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(request.historicalPriceKrw()), 1, RoundingMode.HALF_UP);
    boolean homeAhead = difference >= 0;
    String leader = homeAhead ? "매수" : "전세 + 투자";

    return new Response(
        request.buildingName() + " 비교에서는 " + leader + " 시나리오가 앞섰습니다.",
        "과거 관측가 대비 최근 유사면적 관측가는 " + signed(priceChange)
            + "% 변했습니다. 동일 기간의 대출 원금·이자·보유비와 대체 투자 현금흐름을 함께 계산한 결과, "
            + leader + " 시나리오의 최종 순자산이 " + formatKoreanMoney(Math.abs(difference)) + " 더 높았습니다.",
        List.of(
            new Evidence("관측 가격 변화", signed(priceChange) + "%",
                "과거 거래 중앙값과 최근 유사면적 거래 중앙값의 차이입니다."),
            new Evidence("대출 이자", formatKoreanMoney(request.totalInterestKrw()),
                "매수 시나리오에서 순자산을 낮춘 누적 금융비용입니다."),
            new Evidence("거래 표본", request.observedTradeCount() + "건 · " + request.confidence(),
                "표본 수가 적으면 가격 범위의 불확실성이 커집니다."),
            new Evidence("계산 버전", request.calculationVersion(),
                "같은 입력과 버전으로 결과를 재현할 수 있습니다.")),
        List.of(
            "합성 데모 데이터에 대한 설명이며 실제 투자 판단 근거가 아닙니다.",
            "가격지수·환율·세금·수수료 정의가 바뀌면 결과도 달라집니다.",
            "문장은 결정론적 템플릿으로 생성되며 계산 결과를 변경하지 않습니다."),
        "evidence-explanation-1.0.0");
  }

  private String signed(BigDecimal value) {
    return (value.signum() >= 0 ? "+" : "") + value.toPlainString();
  }

  private String formatKoreanMoney(long amount) {
    if (amount >= 100_000_000L) {
      return BigDecimal.valueOf(amount)
          .divide(BigDecimal.valueOf(100_000_000L), 1, RoundingMode.HALF_UP)
          .stripTrailingZeros().toPlainString() + "억원";
    }
    if (amount >= 10_000L) {
      return BigDecimal.valueOf(amount)
          .divide(BigDecimal.valueOf(10_000L), 1, RoundingMode.HALF_UP)
          .stripTrailingZeros().toPlainString() + "만원";
    }
    return amount + "원";
  }
}
