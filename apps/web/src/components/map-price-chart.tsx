"use client";

import { LineChart } from "echarts/charts";
import { GridComponent, MarkLineComponent, TooltipComponent } from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { useEffect, useRef } from "react";
import type { MapMode, Trade } from "@/lib/types";

echarts.use([LineChart, GridComponent, MarkLineComponent, TooltipComponent, CanvasRenderer]);

const currency = new Intl.NumberFormat("ko-KR");

export default function MapPriceChart({
  trades,
  mode,
  selectedMonth,
}: {
  trades: Trade[];
  mode: MapMode;
  selectedMonth: string;
}) {
  const elementRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!elementRef.current) return;
    const chart = echarts.init(elementRef.current);
    const ordered = [...trades].sort((a, b) => a.contractDate.localeCompare(b.contractDate));
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    chart.setOption({
      animation: !reducedMotion,
      animationDuration: 420,
      grid: { left: 6, right: 10, top: 16, bottom: 22, containLabel: true },
      tooltip: {
        trigger: "axis",
        borderWidth: 0,
        backgroundColor: "#191f28",
        textStyle: { color: "#fff", fontSize: 12, fontWeight: 600 },
        valueFormatter: (value: unknown) => `${currency.format(Number(value))}원`,
      },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: ordered.map((trade) => trade.contractDate.slice(0, 7).replace("-", ".")),
        axisLine: { lineStyle: { color: "#eef0f3" } },
        axisTick: { show: false },
        axisLabel: { color: "#8b95a1", fontSize: 11, fontWeight: 550, interval: 1 },
      },
      yAxis: {
        type: "value",
        scale: true,
        axisLabel: {
          color: "#8b95a1",
          fontSize: 11,
          fontWeight: 550,
          formatter: (value: number) => `${(value / 100_000_000).toFixed(1)}억`,
        },
        splitLine: { lineStyle: { color: "#f2f4f6" } },
      },
      series: [{
        type: "line",
        data: ordered.map((trade) => trade.priceKrw),
        smooth: 0.28,
        showSymbol: ordered.length < 8,
        symbolSize: 6,
        lineStyle: { color: mode === "PAST" ? "#4c6ef5" : "#ff6b3d", width: 3 },
        itemStyle: { color: mode === "PAST" ? "#4c6ef5" : "#ff6b3d" },
        areaStyle: { color: mode === "PAST" ? "rgba(76,110,245,.12)" : "rgba(255,107,61,.12)" },
        markLine: mode === "PAST" ? {
          silent: true,
          symbol: "none",
          lineStyle: { color: "#3451d1", type: "dashed" },
          label: { formatter: selectedMonth.replace("-", "."), color: "#3451d1", fontSize: 11, fontWeight: 650 },
          data: [{ xAxis: selectedMonth.replace("-", ".") }],
        } : undefined,
      }],
    });
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(elementRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
    };
  }, [mode, selectedMonth, trades]);

  if (trades.length === 0) {
    return <div className="chart-empty">비교할 거래 관측치가 없습니다.</div>;
  }
  return <div className="map-price-chart" ref={elementRef} aria-label="실거래가 추이 차트" />;
}
