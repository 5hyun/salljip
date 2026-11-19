package com.housinglab.api.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CalculationEngineTest {
  private final CalculationEngine engine = new CalculationEngine();

  static Stream<Arguments> goldenCases() {
    return Stream.of(
        Arguments.of(0L, "0.0", 0L, 0L),
        Arguments.of(100_000_000L, "0.0", 0L, 0L),
        Arguments.of(100_000_000L, "3.5", 0L, 300_000L),
        Arguments.of(100_000_000L, "8.0", 20_000_000L, 700_000L),
        Arguments.of(250_000_000L, "4.2", 15_000_000L, 0L),
        Arguments.of(300_000_000L, "6.5", 30_000_000L, 1_200_000L),
        Arguments.of(50_000_000L, "0.1", 0L, 450_000L),
        Arguments.of(200_000_000L, "2.9", 8_000_000L, 950_000L),
        Arguments.of(400_000_000L, "5.1", 40_000_000L, 1_500_000L),
        Arguments.of(10_000_000L, "12.0", 1_000_000L, 100_000L),
        Arguments.of(0L, "10.0", 0L, 2_000_000L),
        Arguments.of(150_000_000L, "3.0", 5_000_000L, 0L),
        Arguments.of(180_000_000L, "4.0", 12_000_000L, 600_000L),
        Arguments.of(220_000_000L, "5.0", 18_000_000L, 800_000L),
        Arguments.of(275_000_000L, "6.0", 22_000_000L, 1_000_000L),
        Arguments.of(320_000_000L, "7.0", 27_000_000L, 1_100_000L),
        Arguments.of(90_000_000L, "2.5", 3_000_000L, 350_000L),
        Arguments.of(130_000_000L, "3.8", 6_000_000L, 550_000L),
        Arguments.of(360_000_000L, "4.8", 32_000_000L, 1_300_000L),
        Arguments.of(500_000_000L, "9.0", 50_000_000L, 1_800_000L));
  }

  @ParameterizedTest(name = "golden-case-{index}")
  @MethodSource("goldenCases")
  void preservesAccountingInvariants(long loan, String rate, long acquisitionCost, long monthlyRent) {
    long purchase = 600_000_000L;
    long equity = purchase + acquisitionCost - loan;
    var request = new SimulationModels.HousingRequest(purchase, 720_000_000L, equity, loan,
        new BigDecimal(rate), 60, acquisitionCost, 150_000L, 180_000_000L, monthlyRent, new BigDecimal("7.0"));
    var result = engine.calculate(request);
    assertThat(result.remainingLoanKrw()).isZero();
    assertThat(result.monthlyCashFlows()).hasSize(60);
    assertThat(result.totalInterestKrw()).isGreaterThanOrEqualTo(0L);
    assertThat(result.rentAndInvestNetWorthKrw()).isGreaterThanOrEqualTo(180_000_000L);
    assertThat(result.engineVersion()).isEqualTo(CalculationEngine.VERSION);
  }

  @Test
  void isDeterministicApartFromMetadata() {
    var request = new SimulationModels.HousingRequest(600_000_000L, 720_000_000L, 400_000_000L, 200_000_000L,
        new BigDecimal("4.0"), 60, 0L, 100_000L, 200_000_000L, 700_000L, new BigDecimal("6.0"));
    var first = engine.calculate(request);
    var second = engine.calculate(request);
    assertThat(first.homeNetWorthKrw()).isEqualTo(second.homeNetWorthKrw());
    assertThat(first.rentAndInvestNetWorthKrw()).isEqualTo(second.rentAndInvestNetWorthKrw());
    assertThat(first.monthlyCashFlows()).isEqualTo(second.monthlyCashFlows());
  }

  @Test
  void rejectsBudgetBelowPurchaseAndCosts() {
    var request = new SimulationModels.HousingRequest(600_000_000L, 700_000_000L, 300_000_000L, 200_000_000L,
        new BigDecimal("4"), 60, 20_000_000L, 0L, 0L, 0L, BigDecimal.ZERO);
    assertThatThrownBy(() -> engine.calculate(request)).isInstanceOf(IllegalArgumentException.class);
  }
}
