package com.housinglab.api.simulation;

import static com.housinglab.api.simulation.SimulationModels.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CalculationEngine {
  public static final String VERSION = "housing-cashflow-1.0.0";

  public HousingResult calculate(HousingRequest request) {
    validate(request);
    var monthlyRate = request.annualLoanRatePercent().divide(BigDecimal.valueOf(1200), 16, RoundingMode.HALF_UP);
    long principal = request.loanKrw();
    long monthlyPrincipal = request.months() == 0 ? 0 : divideRounded(request.loanKrw(), request.months());
    long totalInterest = 0;
    long investment = Math.max(0, request.equityKrw() - request.rentDepositKrw());
    var monthlyAlternativeRate = monthlyRate(request.alternativeAnnualReturnPercent());
    var cashFlows = new ArrayList<CashFlow>();

    for (int month = 1; month <= request.months(); month++) {
      long interest = money(BigDecimal.valueOf(principal).multiply(monthlyRate));
      long principalPayment = Math.min(principal, monthlyPrincipal);
      if (month == request.months()) principalPayment = principal;
      principal -= principalPayment;
      totalInterest = Math.addExact(totalInterest, interest);
      investment = money(BigDecimal.valueOf(investment).multiply(BigDecimal.ONE.add(monthlyAlternativeRate)));
      long homePayment = Math.addExact(Math.addExact(interest, principalPayment), request.holdingCostMonthlyKrw());
      long rentPayment = request.monthlyRentKrw();
      long cashFlowGap = homePayment - rentPayment;
      investment = Math.addExact(investment, Math.max(0, cashFlowGap));
      if (cashFlowGap < 0) investment = Math.max(0, investment + cashFlowGap);
      cashFlows.add(new CashFlow(month, homePayment, interest, principalPayment, rentPayment, investment));
    }

    long homeNetWorth = request.currentValueKrw() - principal - request.acquisitionCostKrw();
    long rentNetWorth = Math.addExact(request.rentDepositKrw(), investment);
    long holdingCost = Math.multiplyExact(request.holdingCostMonthlyKrw(), request.months());
    long totalHousingCost = Math.addExact(request.acquisitionCostKrw(), Math.addExact(totalInterest, holdingCost));
    return new HousingResult(
        UUID.randomUUID().toString(), homeNetWorth, rentNetWorth, homeNetWorth - rentNetWorth,
        principal, totalInterest, totalHousingCost, List.copyOf(cashFlows),
        Map.of("repayment", "equal-principal", "currency", "KRW", "rentDepositReturnedAtEnd", true),
        VERSION, Instant.now(),
        List.of("세금·대출규제는 사용자 가정이며 금융·법률 상담이 아닙니다.", "전세보증금은 종료 시 회수 가능한 자산으로 한 번만 반영했습니다."));
  }

  private void validate(HousingRequest request) {
    if (request.equityKrw() + request.loanKrw() < request.purchasePriceKrw() + request.acquisitionCostKrw()) {
      throw new IllegalArgumentException("자기자본과 대출 합계가 매수가 및 취득비용보다 작습니다.");
    }
  }

  private BigDecimal monthlyRate(BigDecimal annualPercent) {
    double annual = annualPercent.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP).doubleValue();
    return BigDecimal.valueOf(Math.pow(1.0 + annual, 1.0 / 12.0) - 1.0);
  }

  private long divideRounded(long value, int divisor) {
    return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValueExact();
  }

  private long money(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP).longValueExact(); }
}
