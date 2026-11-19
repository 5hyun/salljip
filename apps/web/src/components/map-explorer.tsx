"use client";

import dynamic from "next/dynamic";
import {
  ArrowLeftRight,
  Building2,
  CalendarClock,
  ChevronDown,
  CircleAlert,
  Clock3,
  Equal,
  Filter,
  History,
  Layers3,
  LoaderCircle,
  MapPin,
  Palette,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  TrendingDown,
  TrendingUp,
  X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, useTransition } from "react";
import { getMapListings, getTrades, searchHistoricalOptions } from "@/lib/api";
import type {
  HistoricalResponse,
  MapFilters,
  MapListing,
  MapMarkerItem,
  MapMode,
  MapStyleId,
  MapViewport,
  SearchForm,
  Trade,
  TransactionType,
} from "@/lib/types";
import { PROPERTY_TYPE_LABEL } from "@/lib/types";

const RealEstateMap = dynamic(() => import("@/components/real-estate-map"), {
  ssr: false,
  loading: () => <div className="map-stage-loading"><LoaderCircle aria-hidden="true" /> 지도를 준비하는 중…</div>,
});
const MapPriceChart = dynamic(() => import("@/components/map-price-chart"), {
  ssr: false,
  loading: () => <div className="detail-chart-loading">가격 추이를 불러오는 중…</div>,
});

const INITIAL_FILTERS: MapFilters = {
  regionCode: "11350",
  transactionType: "SALE",
  priceMin: 0,
  priceMax: 1_500_000_000,
  areaMin: 40,
  areaMax: 85,
};

const INITIAL_HISTORY: SearchForm = {
  regionCode: "11350",
  date: "2021-11",
  equityKrw: 300_000_000,
  loanKrw: 500_000_000,
  areaMin: 40,
  areaMax: 85,
  acquisitionCostKrw: 12_000_000,
};

const compactMoney = new Intl.NumberFormat("ko-KR", {
  notation: "compact",
  maximumFractionDigits: 1,
});
const fullMoney = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  year: "numeric",
  month: "short",
  day: "numeric",
});
const shortDateFormatter = new Intl.DateTimeFormat("ko-KR", { year: "2-digit", month: "2-digit", day: "2-digit" });
const TRANSACTION_LABELS: Record<TransactionType, string> = {
  SALE: "매매",
  JEONSE: "전세",
  MONTHLY: "월세",
};
const MAP_STYLE_CHOICES: Array<{ id: MapStyleId; label: string; description: string }> = [
  { id: "NORMAL", label: "일반", description: "네이버 기본 벡터 지도" },
  { id: "TERRAIN", label: "지형", description: "산과 지형 정보가 강조된 지도" },
  { id: "HYBRID", label: "위성", description: "지명과 도로가 함께 보이는 위성 지도" },
];

function monthToIndex(value: string) {
  const [year, month] = value.split("-").map(Number);
  return year * 12 + month - 1;
}

function indexToMonth(value: number) {
  const year = Math.floor(value / 12);
  const month = (value % 12) + 1;
  return `${year}-${String(month).padStart(2, "0")}`;
}

function tradePrice(trade: Trade) {
  if (trade.transactionType === "MONTHLY") {
    return `보증금 ${compactMoney.format(trade.priceKrw)}원 · 월 ${fullMoney.format(trade.monthlyRentKrw / 10_000)}만원`;
  }
  return `${compactMoney.format(trade.priceKrw)}원`;
}

function percentChange(current: number, previous?: number | null) {
  if (!previous) return null;
  return ((current - previous) / previous) * 100;
}

function listingPriceValue(listing: MapListing, which: "latest" | "previous") {
  const trade = which === "latest" ? listing.latestTrade : listing.previousTrade;
  if (!trade) return null;
  return trade.transactionType === "MONTHLY" ? trade.monthlyRentKrw : trade.priceKrw;
}

function subscribeMobile(callback: () => void) {
  const query = window.matchMedia("(max-width: 900px)");
  query.addEventListener("change", callback);
  return () => query.removeEventListener("change", callback);
}

function getMobileSnapshot() {
  return window.matchMedia("(max-width: 900px)").matches;
}

function getMobileServerSnapshot() {
  return false;
}

function BrandMark() {
  return <svg className="map-brand-mark" viewBox="0 0 40 40" aria-hidden="true">
    <rect width="40" height="40" rx="12" fill="currentColor" />
    <path d="M10 19.4 20 11l10 8.4V29a1.6 1.6 0 0 1-1.6 1.6H11.6A1.6 1.6 0 0 1 10 29z" fill="#fff" />
    <path d="M20 27.4s-4.6-2.7-4.6-5.8a2.4 2.4 0 0 1 4.6-1.1 2.4 2.4 0 0 1 4.6 1.1c0 3.1-4.6 5.8-4.6 5.8z" fill="currentColor" />
  </svg>;
}

function FilterFields({
  draft,
  onChange,
  mode,
}: {
  draft: MapFilters;
  onChange: <K extends keyof MapFilters>(key: K, value: MapFilters[K]) => void;
  mode: MapMode;
}) {
  return <>
    <label className="field-group">
      <span>지역</span>
      <div className="select-wrap">
        <MapPin size={16} aria-hidden="true" />
        <select name="region" value={draft.regionCode} onChange={(event) => onChange("regionCode", event.target.value)} autoComplete="off">
          <option value="11350">서울특별시 노원구</option>
          <option value="11380" disabled>은평구 · 준비 중</option>
        </select>
        <ChevronDown size={15} aria-hidden="true" />
      </div>
    </label>
    {mode === "CURRENT" ? <fieldset className="filter-fieldset">
      <legend>거래 유형</legend>
      <div className="segment-control transaction-segments">
        {(Object.keys(TRANSACTION_LABELS) as TransactionType[]).map((type) => <button
          className={draft.transactionType === type ? "active" : ""}
          key={type}
          type="button"
          onClick={() => onChange("transactionType", type)}
        >{TRANSACTION_LABELS[type]}</button>)}
      </div>
    </fieldset> : null}
    <fieldset className="filter-fieldset">
      <legend>{draft.transactionType === "MONTHLY" ? "보증금" : "가격"}</legend>
      <div className="range-fields">
        <label><span className="sr-only">최소 가격</span><input name="priceMin" type="number" min="0" step="10000000" inputMode="numeric" autoComplete="off" value={draft.priceMin} onChange={(event) => onChange("priceMin", Number(event.target.value))} /></label>
        <i>–</i>
        <label><span className="sr-only">최대 가격</span><input name="priceMax" type="number" min="0" step="10000000" inputMode="numeric" autoComplete="off" value={draft.priceMax} onChange={(event) => onChange("priceMax", Number(event.target.value))} /></label>
      </div>
      <small>{compactMoney.format(draft.priceMin)}원 – {compactMoney.format(draft.priceMax)}원</small>
    </fieldset>
    <fieldset className="filter-fieldset">
      <legend>전용면적</legend>
      <div className="range-fields">
        <label><span className="sr-only">최소 면적</span><input name="areaMin" type="number" min="1" max="300" inputMode="decimal" autoComplete="off" value={draft.areaMin} onChange={(event) => onChange("areaMin", Number(event.target.value))} /></label>
        <i>–</i>
        <label><span className="sr-only">최대 면적</span><input name="areaMax" type="number" min="1" max="300" inputMode="decimal" autoComplete="off" value={draft.areaMax} onChange={(event) => onChange("areaMax", Number(event.target.value))} /></label>
        <b>㎡</b>
      </div>
    </fieldset>
  </>;
}

export default function MapExplorer() {
  const [mode, setMode] = useState<MapMode>("CURRENT");
  const [filters, setFilters] = useState(INITIAL_FILTERS);
  const [draftFilters, setDraftFilters] = useState(INITIAL_FILTERS);
  const [historyForm, setHistoryForm] = useState(INITIAL_HISTORY);
  const [listings, setListings] = useState<MapListing[]>([]);
  const [historyResult, setHistoryResult] = useState<HistoricalResponse | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedTrades, setSelectedTrades] = useState<Trade[]>([]);
  const [viewport, setViewport] = useState<MapViewport | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [mapStyle, setMapStyle] = useState<MapStyleId>("NORMAL");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const isMobile = useSyncExternalStore(subscribeMobile, getMobileSnapshot, getMobileServerSnapshot);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const filterCloseRef = useRef<HTMLButtonElement>(null);

  const candidates = useMemo(() => historyResult?.results ?? [], [historyResult]);
  const normalizedQuery = searchQuery.trim().toLocaleLowerCase("ko-KR");
  const visibleListings = useMemo(() => normalizedQuery
    ? listings.filter((listing) => `${listing.building.name} ${listing.building.address}`.toLocaleLowerCase("ko-KR").includes(normalizedQuery))
    : listings, [listings, normalizedQuery]);
  const visibleCandidates = useMemo(() => normalizedQuery
    ? candidates.filter((candidate) => `${candidate.building.name} ${candidate.building.address}`.toLocaleLowerCase("ko-KR").includes(normalizedQuery))
    : candidates, [candidates, normalizedQuery]);
  const selectedListing = listings.find((listing) => listing.building.id === selectedId) ?? null;
  const selectedCandidate = candidates.find((candidate) => candidate.building.id === selectedId) ?? null;
  const selectedBuilding = mode === "CURRENT" ? selectedListing?.building : selectedCandidate?.building;
  const usesRealTrades = listings.some((listing) => listing.sourceStatus === "PUBLIC_ALLOWED");
  const usesOfficialCatalog = !usesRealTrades && listings.some((listing) => listing.sourceStatus === "NO_MATCHING_TRADE");
  const realDataAsOf = listings.find((listing) => listing.sourceStatus === "PUBLIC_ALLOWED")?.dataAsOf;

  const markers = useMemo<MapMarkerItem[]>(() => {
    if (mode === "CURRENT") {
      return visibleListings.map((listing) => ({
        id: listing.building.id,
        name: listing.building.name,
        longitude: listing.building.longitude,
        latitude: listing.building.latitude,
        primaryPrice: listing.latestTrade?.priceKrw ?? null,
        monthlyRentKrw: listing.latestTrade?.monthlyRentKrw ?? 0,
        changeRatePercent: percentChange(listingPriceValue(listing, "latest") ?? 0, listingPriceValue(listing, "previous")),
        eligible: false,
        confidence: listing.latestTrade && listing.observedTradeCount > 1 ? "HIGH" : "LOW",
      }));
    }
    return visibleCandidates.map((candidate) => ({
      id: candidate.building.id,
      name: candidate.building.name,
      longitude: candidate.building.longitude,
      latitude: candidate.building.latitude,
      primaryPrice: candidate.historicalPriceRange.median,
      monthlyRentKrw: 0,
      changeRatePercent: candidate.changeRatePercent,
      eligible: true,
      confidence: candidate.confidence,
    }));
  }, [mode, visibleCandidates, visibleListings]);

  // 새 조회가 시작되면 이전 조회를 취소한다. 늦게 도착한 옛 응답이 최신 마커를 덮어쓰지 않게 한다.
  const currentRequestRef = useRef<AbortController | null>(null);
  const loadCurrent = useCallback((nextFilters: MapFilters, bbox?: string) => {
    currentRequestRef.current?.abort();
    const controller = new AbortController();
    currentRequestRef.current = controller;
    startTransition(async () => {
      try {
        const data = await getMapListings(nextFilters, bbox, controller.signal);
        if (controller.signal.aborted) return;
        setListings(data);
        setError(null);
        setSelectedId((current) => data.some((item) => item.building.id === current) ? current : data[0]?.building.id ?? null);
      } catch (cause) {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? `${cause.message} 조건을 확인하고 다시 시도해 주세요.` : "현재 지도 데이터를 불러오지 못했습니다. 다시 시도해 주세요.");
      }
    });
  }, []);

  const loadHistory = useCallback((nextForm: SearchForm) => {
    startTransition(async () => {
      try {
        const data = await searchHistoricalOptions(nextForm);
        setHistoryResult(data);
        setError(null);
        setSelectedId((current) => data.results.some((item) => item.building.id === current) ? current : data.results[0]?.building.id ?? null);
      } catch (cause) {
        setError(cause instanceof Error ? `${cause.message} 날짜·예산·면적을 확인해 주세요.` : "과거 지도를 불러오지 못했습니다. 다시 시도해 주세요.");
      }
    });
  }, []);

  useEffect(() => {
    loadCurrent(INITIAL_FILTERS);
  }, [loadCurrent]);

  useEffect(() => {
    if (!selectedId) return;
    const type = mode === "PAST" ? "SALE" : filters.transactionType;
    getTrades(selectedId, type).then(setSelectedTrades).catch(() => setSelectedTrades([]));
  }, [filters.transactionType, mode, selectedId]);

  useEffect(() => {
    if (!viewport || mode !== "CURRENT") return;
    const timer = window.setTimeout(() => loadCurrent(filters, viewport.bbox), 280);
    return () => window.clearTimeout(timer);
  }, [filters, loadCurrent, mode, viewport]);

  useEffect(() => {
    const query = new URLSearchParams({
      mode: mode.toLowerCase(),
      type: filters.transactionType.toLowerCase(),
      area: `${filters.areaMin}-${filters.areaMax}`,
    });
    if (mode === "PAST") query.set("month", historyForm.date);
    window.history.replaceState(null, "", `?${query}`);
  }, [filters.areaMax, filters.areaMin, filters.transactionType, historyForm.date, mode]);

  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLocaleLowerCase() === "k") {
        event.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);

  useEffect(() => {
    if (filterOpen && isMobile) filterCloseRef.current?.focus();
  }, [filterOpen, isMobile]);

  function updateDraft<K extends keyof MapFilters>(key: K, value: MapFilters[K]) {
    setDraftFilters((current) => ({ ...current, [key]: value }));
  }

  function switchMode(nextMode: MapMode) {
    setMode(nextMode);
    setFilterOpen(false);
    if (nextMode === "PAST") {
      const nextHistory = {
        ...historyForm,
        regionCode: filters.regionCode,
        areaMin: filters.areaMin,
        areaMax: filters.areaMax,
      };
      setHistoryForm(nextHistory);
      loadHistory(nextHistory);
    } else {
      loadCurrent(filters, viewport?.bbox);
    }
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    if (draftFilters.priceMin > draftFilters.priceMax || draftFilters.areaMin > draftFilters.areaMax) {
      setError("최솟값은 최댓값보다 작아야 합니다. 입력값을 확인해 주세요.");
      return;
    }
    setFilters(draftFilters);
    setHistoryForm((current) => ({ ...current, regionCode: draftFilters.regionCode, areaMin: draftFilters.areaMin, areaMax: draftFilters.areaMax }));
    setFilterOpen(false);
    if (mode === "CURRENT") loadCurrent(draftFilters, viewport?.bbox);
  }

  function submitHistory(event: FormEvent) {
    event.preventDefault();
    loadHistory(historyForm);
    setFilterOpen(false);
  }

  function selectApartment(id: string) {
    setSelectedId(id);
    setDetailOpen(true);
    if (isMobile) setFilterOpen(false);
  }

  function goToPast() {
    const nextHistory = { ...historyForm, date: "2021-11" };
    setHistoryForm(nextHistory);
    setMode("PAST");
    loadHistory(nextHistory);
  }

  const currentChange = selectedListing
    ? percentChange(listingPriceValue(selectedListing, "latest") ?? 0, listingPriceValue(selectedListing, "previous"))
    : null;

  return <div className={`map-app ${mode === "PAST" ? "past-mode" : "current-mode"}`}>
    <a href="#map-workspace" className="skip-link">지도 탐색으로 건너뛰기</a>
    <header className="map-header">
      <div className="map-brand" aria-label="우리 살 집">
        <BrandMark />
        <span><strong>우리 살 집</strong><small>우리 가족, 어디서 살까?</small></span>
      </div>
      <div className="mode-switch" role="group" aria-label="지도 시점">
        <button type="button" className={mode === "CURRENT" ? "active" : ""} aria-pressed={mode === "CURRENT"} onClick={() => switchMode("CURRENT")}><MapPin size={15} aria-hidden="true" />지금</button>
        <button type="button" className={mode === "PAST" ? "active" : ""} aria-pressed={mode === "PAST"} onClick={() => switchMode("PAST")}><History size={15} aria-hidden="true" />그때라면</button>
      </div>
      <div className="header-context">
        <span className="demo-status"><i /> {usesRealTrades ? "국토교통부 실거래" : usesOfficialCatalog ? "서울시 단지 정보" : "합성 데모 데이터"}</span>
        <button type="button" className="mobile-filter-button" onClick={() => setFilterOpen(true)} aria-label="찾는 조건 열기"><SlidersHorizontal size={16} aria-hidden="true" /><span>조건</span></button>
      </div>
    </header>

    <main className="map-workspace" id="map-workspace">
      <aside className={`filter-panel ${filterOpen ? "open" : "peek"}`} aria-label="찾는 조건과 단지 목록">
        <button type="button" className="sheet-grabber" onClick={() => setFilterOpen((open) => !open)} aria-label={filterOpen ? "목록 접기" : "조건과 목록 펼치기"}><i aria-hidden="true" /></button>
        <div className="mobile-sheet-heading"><strong>찾는 조건</strong><button ref={filterCloseRef} type="button" onClick={() => setFilterOpen(false)} aria-label="닫기"><X size={20} aria-hidden="true" /></button></div>
        <div className="panel-intro">
          <span>{mode === "PAST" ? "그때라면" : "지금 우리 동네"}</span>
          <h1>{mode === "PAST" ? "그때 이 돈이면, 어디에 살 수 있었을까?" : "노원구, 지금은 얼마에 살 수 있을까?"}</h1>
          <p>{mode === "PAST" ? "그 시절 실제로 거래된 집과 요즘 비슷한 집의 가격을 나란히 보여드려요." : "최근 실제로 거래된 가격을 지도에서 편하게 둘러보세요."}</p>
        </div>

        <form onSubmit={applyFilters} className="filter-form">
          <FilterFields draft={draftFilters} onChange={updateDraft} mode={mode} />
          <button className="primary-action" type="submit" disabled={isPending}>{isPending ? <LoaderCircle className="spin" size={17} aria-hidden="true" /> : <Filter size={17} aria-hidden="true" />}이 조건으로 보기</button>
        </form>

        {mode === "PAST" ? <form className="history-form" onSubmit={submitHistory}>
          <div className="history-form-title"><CalendarClock size={17} aria-hidden="true" /><div><strong>언제, 얼마로 찾아볼까요?</strong><small>그때 거래된 기록이에요. 실제 매물이나 대출 가능 여부와는 다를 수 있어요.</small></div></div>
          <label className="field-group"><span>언제</span><input name="historyMonth" type="month" min="2018-01" max="2026-11" value={historyForm.date} onChange={(event) => setHistoryForm((current) => ({ ...current, date: event.target.value }))} /></label>
          <label className="field-group"><span>가진 돈</span><div className="input-with-unit"><input name="equity" type="number" min="0" step="10000000" inputMode="numeric" autoComplete="off" value={historyForm.equityKrw} onChange={(event) => setHistoryForm((current) => ({ ...current, equityKrw: Number(event.target.value) }))} /><b>원</b></div><small>{compactMoney.format(historyForm.equityKrw)}원</small></label>
          <label className="field-group"><span>대출 (가정)</span><div className="input-with-unit"><input name="loan" type="number" min="0" step="10000000" inputMode="numeric" autoComplete="off" value={historyForm.loanKrw} onChange={(event) => setHistoryForm((current) => ({ ...current, loanKrw: Number(event.target.value) }))} /><b>원</b></div><small>합쳐서 {compactMoney.format(historyForm.equityKrw + historyForm.loanKrw)}원</small></label>
          <button className="history-search-button" type="submit" disabled={isPending}><Search size={16} aria-hidden="true" />그때 살 수 있던 집 찾기</button>
        </form> : null}

        <div className="result-summary" aria-live="polite">
          <div><span>{mode === "PAST" ? "예산 안에 들어온 단지" : "지도 안의 단지"}</span><strong>{markers.length}</strong></div>
          <span>{viewport ? `확대 ${viewport.zoom.toFixed(1)}` : "지도 준비 중"}</span>
        </div>
        <div className="result-list">
          {markers.length === 0 && !isPending ? <div className="empty-state"><Search size={24} aria-hidden="true" /><strong>조건에 맞는 집이 아직 없어요</strong><p>가격이나 면적 범위를 조금 넓히거나, 지도를 옮겨 보세요.</p></div> : null}
          {mode === "CURRENT" ? visibleListings.map((listing) => {
            const change = percentChange(listingPriceValue(listing, "latest") ?? 0, listingPriceValue(listing, "previous"));
            return <button type="button" className={`result-item ${selectedId === listing.building.id ? "selected" : ""}`} key={listing.building.id} onClick={() => selectApartment(listing.building.id)}>
              <span className="result-item-icon"><Building2 size={16} aria-hidden="true" /></span>
              <span className="result-item-main"><strong>{listing.building.name}{listing.building.propertyType && listing.building.propertyType !== "APT" ? ` · ${PROPERTY_TYPE_LABEL[listing.building.propertyType]}` : ""}</strong>{listing.latestTrade ? <><small>{listing.latestTrade.areaM2} ㎡ · {listing.latestTrade.floor}층 · {listing.latestTrade.contractDate}</small><b>{tradePrice(listing.latestTrade)}</b></> : <><small>{listing.building.address}</small><b className="trade-unavailable">최근 거래 없음</b></>}</span>
              {change !== null ? <span className={`mini-change ${change >= 0 ? "up" : "down"}`}>{change >= 0 ? "+" : ""}{change.toFixed(1)}%</span> : <Equal size={14} aria-label="비교 거래 없음" />}
            </button>;
          }) : visibleCandidates.map((candidate) => <button type="button" className={`result-item historical ${selectedId === candidate.building.id ? "selected" : ""}`} key={candidate.building.id} onClick={() => selectApartment(candidate.building.id)}>
            <span className="result-item-icon"><Clock3 size={16} aria-hidden="true" /></span>
            <span className="result-item-main"><strong>{candidate.building.name}</strong><small>{candidate.matchedAreaMin} ㎡ · 관측 {candidate.observedTradeCount}건</small><b>{compactMoney.format(candidate.historicalPriceRange.median)}원</b></span>
            <span className={`mini-change ${(candidate.changeRatePercent ?? 0) >= 0 ? "up" : "down"}`}>{candidate.changeRatePercent === null ? "비교 불가" : `${candidate.changeRatePercent >= 0 ? "+" : ""}${candidate.changeRatePercent}%`}</span>
          </button>)}
        </div>
      </aside>

      {filterOpen ? <button type="button" className="drawer-backdrop" aria-label="필터 닫기" onClick={() => setFilterOpen(false)} /> : null}

      <section className="map-stage" aria-label="부동산 지도">
        <RealEstateMap markers={markers} mode={mode} mapStyle={mapStyle} selectedId={selectedId} onSelect={selectApartment} onViewportChange={setViewport} />
        <div className="map-floating-search"><Search size={17} aria-hidden="true" /><input ref={searchInputRef} type="search" name="apartmentSearch" placeholder="아파트 이름이나 동네 검색…" aria-label="아파트 이름이나 동네 검색" autoComplete="off" value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} /><kbd>⌘ K</kbd></div>
        <div className="map-style-control" role="group" aria-label="지도 스타일">
          <span className="map-style-control-label"><Palette size={13} aria-hidden="true" /> 지도</span>
          {MAP_STYLE_CHOICES.map((style) => <button
            type="button"
            key={style.id}
            className={mapStyle === style.id ? "active" : ""}
            aria-pressed={mapStyle === style.id}
            title={style.description}
            onClick={() => setMapStyle(style.id)}
          ><i className={`style-swatch ${style.id.toLowerCase()}`} aria-hidden="true" />{style.label}</button>)}
        </div>
        <div className="map-legend-card">
          <span><i className={mode === "PAST" ? "past" : "current"} />{mode === "PAST" ? "예산 안의 그때 거래" : `${TRANSACTION_LABELS[filters.transactionType]} 최신 거래`}</span>
          {mode === "PAST" ? <span><i className="low" />거래 기록 적음</span> : null}
        </div>
        <div className="map-count-pill"><Layers3 size={14} aria-hidden="true" /> {markers.length}개 단지 표시</div>

        {mode === "PAST" ? <div className="time-dock">
          <div className="time-dock-heading"><span><History size={16} aria-hidden="true" /> 그때라면</span><strong>{historyForm.date.replace("-", ".")}</strong></div>
          <input
            type="range"
            min={monthToIndex("2018-01")}
            max={monthToIndex("2026-11")}
            value={monthToIndex(historyForm.date)}
            aria-label="과거 기준 월"
            onChange={(event) => setHistoryForm((current) => ({ ...current, date: indexToMonth(Number(event.target.value)) }))}
            onPointerUp={(event) => {
              const next = { ...historyForm, date: indexToMonth(Number(event.currentTarget.value)) };
              setHistoryForm(next);
              loadHistory(next);
            }}
            onKeyUp={(event) => {
              const next = { ...historyForm, date: indexToMonth(Number(event.currentTarget.value)) };
              setHistoryForm(next);
              loadHistory(next);
            }}
          />
          <div className="time-labels"><span>2018</span><span>2021</span><span>2024</span><span>현재</span></div>
        </div> : null}
        {error ? <div className="map-error" role="alert"><CircleAlert size={17} aria-hidden="true" /><span>{error}</span><button type="button" onClick={() => mode === "CURRENT" ? loadCurrent(filters, viewport?.bbox) : loadHistory(historyForm)}>다시 시도</button></div> : null}
      </section>

      {(!isMobile || detailOpen) ? <aside className={`detail-panel ${detailOpen ? "mobile-open" : ""} ${selectedBuilding ? "" : "is-empty"}`} aria-label="선택 단지 상세 정보">
        <div className="detail-mobile-grabber" />
        <button type="button" className="detail-close" onClick={() => setDetailOpen(false)} aria-label="상세 패널 닫기"><X size={19} aria-hidden="true" /></button>
        {!selectedBuilding ? <div className="detail-placeholder"><span><MapPin size={22} aria-hidden="true" /></span><h2>마음에 드는 단지를 눌러보세요</h2><p>지도 위 가격표를 누르면 최근 거래와 가격 흐름을 보여드려요.</p></div> : <>
          <div className="detail-hero">
            <div className="detail-tags">{selectedBuilding.propertyType ? <span>{PROPERTY_TYPE_LABEL[selectedBuilding.propertyType]}</span> : null}<span>{mode === "PAST" ? `${historyForm.date.replace("-", ".")} 관측` : "최신 관측"}</span><span className="source-tag"><ShieldCheck size={11} aria-hidden="true" /> {usesRealTrades ? "국토교통부 실거래" : "데모 출처"}</span></div>
            <h2>{selectedBuilding.name}</h2>
            <p><MapPin size={13} aria-hidden="true" /> {selectedBuilding.address}</p>
            <div className="building-facts"><span><b>{selectedBuilding.households > 0 ? fullMoney.format(selectedBuilding.households) : "-"}</b>세대</span><span><b>{selectedBuilding.builtYear > 0 ? selectedBuilding.builtYear : "-"}</b>년 준공</span><span><b>{selectedBuilding.builtYear > 0 ? 2026 - selectedBuilding.builtYear : "-"}</b>년차</span></div>
          </div>

          {mode === "CURRENT" && selectedListing?.latestTrade ? <>
            <div className="price-spotlight">
              <span>최근 {TRANSACTION_LABELS[selectedListing.latestTrade.transactionType]} 실거래</span>
              <strong>{tradePrice(selectedListing.latestTrade)}</strong>
              <small>{selectedListing.latestTrade.areaM2} ㎡ · {selectedListing.latestTrade.floor}층 · {dateFormatter.format(new Date(`${selectedListing.latestTrade.contractDate}T00:00:00+09:00`))}</small>
              {currentChange !== null ? <div className={`spotlight-change ${currentChange >= 0 ? "up" : "down"}`}>{currentChange >= 0 ? <TrendingUp size={15} aria-hidden="true" /> : <TrendingDown size={15} aria-hidden="true" />} 직전 관측 대비 {currentChange >= 0 ? "+" : ""}{currentChange.toFixed(1)}%</div> : null}
            </div>
            <button type="button" className="time-travel-cta" onClick={goToPast}><span><History size={19} aria-hidden="true" /><span><strong>2021년이었다면?</strong><small>그때 이 동네에서 살 수 있었던 집 보기</small></span></span><ArrowLeftRight size={18} aria-hidden="true" /></button>
          </> : mode === "CURRENT" && selectedListing ? <div className="price-spotlight unavailable">
            <span>최근 실거래</span>
            <strong>거래 관측 없음</strong>
            <small>선택한 가격·면적 조건에서 비교할 거래가 없습니다.</small>
          </div> : null}

          {mode === "PAST" && selectedCandidate ? <div className="past-comparison-card">
            <div className="past-comparison-heading"><span><Sparkles size={14} aria-hidden="true" /> 그때 예산 안에서 거래됨</span><small>{selectedCandidate.confidence === "HIGH" ? "거래 기록 충분" : "거래 기록 적음"}</small></div>
            <div className="price-bridge">
              <div><span>{historyForm.date.replace("-", ".")}</span><strong>{compactMoney.format(selectedCandidate.historicalPriceRange.median)}원</strong><small>그때 거래 가운데값</small></div>
              <ArrowLeftRight size={19} aria-hidden="true" />
              <div><span>최근</span><strong>{selectedCandidate.currentComparablePriceRange ? `${compactMoney.format(selectedCandidate.currentComparablePriceRange.median)}원` : "데이터 부족"}</strong><small>비슷한 평수 가운데값</small></div>
            </div>
            {selectedCandidate.changeRatePercent !== null ? <div className={`comparison-result ${selectedCandidate.changeRatePercent >= 0 ? "up" : "down"}`}><strong>{selectedCandidate.changeRatePercent >= 0 ? "+" : ""}{selectedCandidate.changeRatePercent}%</strong><span>그때보다 요즘 거래가격 변화</span></div> : <div className="comparison-unavailable">요즘 비슷한 평수 거래가 없어서 변화율은 계산하지 않았어요.</div>}
          </div> : null}

          <section className="detail-section">
            <div className="detail-section-heading"><div><h3>가격 흐름</h3><span>실제 신고된 거래</span></div></div>
            <MapPriceChart trades={selectedTrades} mode={mode} selectedMonth={historyForm.date} />
          </section>

          <section className="detail-section recent-trades">
            <div className="detail-section-heading"><div><h3>최근 거래</h3><span>{selectedTrades.length}건</span></div></div>
            <div className="trade-table" role="table" aria-label="최근 실거래 내역">
              {selectedTrades.slice().sort((a, b) => b.contractDate.localeCompare(a.contractDate)).slice(0, 5).map((trade) => <div className="trade-row" role="row" key={trade.id}>
                <span><b>{shortDateFormatter.format(new Date(`${trade.contractDate}T00:00:00+09:00`))}</b><small>{trade.areaM2} ㎡ · {trade.floor}층</small></span><strong>{tradePrice(trade)}</strong>
              </div>)}
              {selectedTrades.length === 0 ? <div className="trade-empty">이 조건으로는 거래 기록이 없어요.</div> : null}
            </div>
          </section>

          {mode === "PAST" ? <div className="data-caution"><CircleAlert size={16} aria-hidden="true" /><p><strong>알아두세요</strong>그때 예산 안에서 거래가 신고됐다는 뜻이에요. 당시 실제로 매물이 있었는지, 대출이 됐을지는 보장하지 않아요.</p></div> : null}
          <div className="data-footnote"><ShieldCheck size={14} aria-hidden="true" /><span>{usesRealTrades ? `기준일 ${realDataAsOf ?? ""} · 출처: 국토교통부 실거래가 공개시스템(공공데이터포털) · 위치: 브이월드 지오코더 · 매매만 제공` : usesOfficialCatalog ? "단지 위치·기본정보: 서울 열린데이터광장 · 가격: 관측 데이터가 있는 단지만 표시" : `기준일 ${dateFormatter.format(new Date("2026-11-17T00:00:00+09:00"))} · 국토교통부 형식 합성 데이터`}<br />거래가 없으면 가격을 짐작해서 채우지 않고 “기록 부족”으로 보여드려요.</span></div>
        </>}
      </aside> : null}
    </main>
  </div>;
}
