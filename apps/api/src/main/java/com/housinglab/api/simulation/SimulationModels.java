package com.housinglab.api.simulation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SimulationModels {
  private SimulationModels() {}
  public record HousingRequest(
      @Positive long purchasePriceKrw,
      @Positive long currentValueKrw,
      @PositiveOrZero long equityKrw,
      @PositiveOrZero long loanKrw,
      @DecimalMin("0.0") @DecimalMax("30.0") BigDecimal annualLoanRatePercent,
      @Min(1) @Max(480) int months,
      @PositiveOrZero long acquisitionCostKrw,
      @PositiveOrZero long holdingCostMonthlyKrw,
      @PositiveOrZero long rentDepositKrw,
      @PositiveOrZero long monthlyRentKrw,
      @DecimalMin("-99.0") @DecimalMax("1000.0") BigDecimal alternativeAnnualReturnPercent) {}
  public record CashFlow(int month, long homePayment, long loanInterest, long loanPrincipal, long rentPayment, long investmentBalance) {}
  public record HousingResult(
      String id,
      long homeNetWorthKrw,
      long rentAndInvestNetWorthKrw,
      long differenceKrw,
      long remainingLoanKrw,
      long totalInterestKrw,
      long totalHousingCostKrw,
      List<CashFlow> monthlyCashFlows,
      Map<String, Object> assumptions,
      String engineVersion,
      Instant calculatedAt,
      List<String> warnings) {}
  public record AssetRequest(@Positive long initialKrw, @Min(1) @Max(480) int months) {}
  public record AssetResult(String assetKey, String displayName, long initialKrw, long finalKrw, BigDecimal returnPercent, String basis, String sourceStatus) {}
  public record AssetComparison(List<AssetResult> assets, Instant dataAsOf, List<String> warnings) {}
}
