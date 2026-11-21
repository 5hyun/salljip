"use client";

import { useEffect, useRef, useState } from "react";
import type { MapMarkerItem, MapMode, MapStyleId, MapViewport } from "@/lib/types";

const NAVER_MAP_SCRIPT_ID = "naver-map-sdk";
const NAVER_MAP_CLIENT_ID = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID;
const INITIAL_ZOOM = 16;
// 첫 화면은 상계주공12단지(동일로 1538)를 중심으로 연다.
const INITIAL_CENTER = { lat: 37.6673, lng: 127.0607 };
const CLUSTER_ZOOM = 11.5;
// "none" 이면 네이버 SDK를 아예 불러오지 않는다(개발·테스트에서 Dynamic Map 이용량 절약).
const MAP_PROVIDER = process.env.NEXT_PUBLIC_MAP_PROVIDER === "none" ? "none" : "naver";
const AUTOMATED_AGENT = /bot|crawler|spider|crawling|headless|lighthouse/i;
// 지도 없이도 목록 조회가 돌도록 노원구 전체를 덮는 기본 범위.
const FALLBACK_VIEWPORT: MapViewport = {
  bbox: "127.035,37.605,127.105,37.69",
  zoom: INITIAL_ZOOM,
  center: [INITIAL_CENTER.lng, INITIAL_CENTER.lat],
};
let naverMapLoader: Promise<void> | null = null;

const MAP_TYPE_IDS: Record<MapStyleId, naver.maps.MapTypeIdLiteral> = {
  NORMAL: "normal",
  TERRAIN: "terrain",
  HYBRID: "hybrid",
};

type RealEstateMapProps = {
  markers: MapMarkerItem[];
  mode: MapMode;
  mapStyle: MapStyleId;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onViewportChange: (viewport: MapViewport) => void;
};

const compactMoney = new Intl.NumberFormat("ko-KR", {
  notation: "compact",
  maximumFractionDigits: 1,
});

function loadNaverMapSdk(clientId: string) {
  if (typeof naver !== "undefined" && naver.maps) return Promise.resolve();
  if (naverMapLoader) return naverMapLoader;

  naverMapLoader = new Promise<void>((resolve, reject) => {
    const existing = document.getElementById(NAVER_MAP_SCRIPT_ID) as HTMLScriptElement | null;
    const script = existing ?? document.createElement("script");
    const handleError = () => {
      naverMapLoader = null;
      reject(new Error("NAVER_MAP_SDK_LOAD_FAILED"));
    };

    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", handleError, { once: true });
    if (!existing) {
      script.id = NAVER_MAP_SCRIPT_ID;
      script.async = true;
      script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}&submodules=gl`;
      document.head.appendChild(script);
    }
  });

  return naverMapLoader;
}

function mapDisabledReason() {
  if (MAP_PROVIDER === "none") return "지도 제공자가 꺼져 있습니다 (NEXT_PUBLIC_MAP_PROVIDER=none).";
  if (navigator.webdriver || AUTOMATED_AGENT.test(navigator.userAgent)) return "자동화된 브라우저에서는 네이버 지도를 불러오지 않습니다.";
  return null;
}

// 백그라운드 탭·프리렌더 상태에서는 지도 생성(=과금 1건)을 화면에 보일 때까지 미룬다.
function whenDocumentVisible(callback: () => void) {
  if (document.visibilityState === "visible") {
    callback();
    return () => {};
  }
  const handleChange = () => {
    if (document.visibilityState !== "visible") return;
    document.removeEventListener("visibilitychange", handleChange);
    callback();
  };
  document.addEventListener("visibilitychange", handleChange);
  return () => document.removeEventListener("visibilitychange", handleChange);
}

function priceLabel(marker: MapMarkerItem) {
  if (marker.primaryPrice === null) return "거래 없음";
  if (marker.monthlyRentKrw > 0) {
    return `${compactMoney.format(marker.primaryPrice)}/${Math.round(marker.monthlyRentKrw / 10_000)}만`;
  }
  return `${compactMoney.format(marker.primaryPrice)}원`;
}

function clearMarkers(markers: naver.maps.Marker[]) {
  markers.forEach((marker) => marker.setMap(null));
}

export default function RealEstateMap({
  markers,
  mode,
  mapStyle,
  selectedId,
  onSelect,
  onViewportChange,
}: RealEstateMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<naver.maps.Map | null>(null);
  const mapMarkersRef = useRef<naver.maps.Marker[]>([]);
  const initialMapStyleRef = useRef(mapStyle);
  const onSelectRef = useRef(onSelect);
  const onViewportChangeRef = useRef(onViewportChange);
  const [mapReady, setMapReady] = useState(false);
  const [disabledReason] = useState(mapDisabledReason);
  const [loadError, setLoadError] = useState<string | null>(() => NAVER_MAP_CLIENT_ID ? null : "네이버 지도 Client ID가 설정되지 않았습니다.");

  useEffect(() => {
    onSelectRef.current = onSelect;
    onViewportChangeRef.current = onViewportChange;
  }, [onSelect, onViewportChange]);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    if (disabledReason || !NAVER_MAP_CLIENT_ID) {
      onViewportChangeRef.current(FALLBACK_VIEWPORT);
      return;
    }
    const clientId = NAVER_MAP_CLIENT_ID;

    let disposed = false;
    let idleListener: naver.maps.MapEventListener | null = null;

    const stopWaiting = whenDocumentVisible(() => loadNaverMapSdk(clientId).then(() => {
      if (disposed || !containerRef.current) return;
      const map = new naver.maps.Map(containerRef.current, {
        gl: true,
        center: new naver.maps.LatLng(INITIAL_CENTER.lat, INITIAL_CENTER.lng),
        zoom: INITIAL_ZOOM,
        minZoom: 9,
        maxZoom: 20,
        mapTypeId: MAP_TYPE_IDS[initialMapStyleRef.current],
        zoomControl: true,
        zoomControlOptions: { position: naver.maps.Position.LEFT_BOTTOM, style: naver.maps.ZoomControlStyle.SMALL },
        scaleControl: false,
      });

      const reportViewport = () => {
        const bounds = map.getBounds() as naver.maps.LatLngBounds;
        const center = map.getCenter() as naver.maps.LatLng;
        if (process.env.NODE_ENV !== "production") {
          console.log(`[map] zoom=${map.getZoom()} center=${center.lat().toFixed(6)},${center.lng().toFixed(6)}`);
        }
        onViewportChangeRef.current({
          bbox: [bounds.west(), bounds.south(), bounds.east(), bounds.north()].join(","),
          zoom: map.getZoom(),
          center: [center.lng(), center.lat()],
        });
      };

      idleListener = naver.maps.Event.addListener(map, "idle", reportViewport);
      mapRef.current = map;
      // 데스크톱에서는 오른쪽 상세 카드가 지도를 덮으므로, 보이는 영역 가운데로 시작 위치를 옮긴다.
      if (window.matchMedia("(min-width: 901px)").matches) {
        const rootStyle = getComputedStyle(document.documentElement);
        const covered = parseFloat(rootStyle.getPropertyValue("--detail")) + parseFloat(rootStyle.getPropertyValue("--gap"));
        if (covered > 0) map.panBy(new naver.maps.Point(covered / 2, 0));
      }
      setMapReady(true);
      setLoadError(null);
      reportViewport();
    }).catch(() => {
      if (!disposed) setLoadError("네이버 지도 인증에 실패했습니다. Dynamic Map 설정과 허용 URL을 확인해 주세요.");
    }));

    return () => {
      disposed = true;
      stopWaiting();
      if (idleListener) naver.maps.Event.removeListener(idleListener);
      clearMarkers(mapMarkersRef.current);
      mapMarkersRef.current = [];
      mapRef.current?.destroy();
      mapRef.current = null;
    };
  }, [disabledReason]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;
    map.setMapTypeId(MAP_TYPE_IDS[mapStyle]);
  }, [mapReady, mapStyle]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;

    const renderMarkers = () => {
      clearMarkers(mapMarkersRef.current);
      mapMarkersRef.current = [];
      if (markers.length === 0) return;

      const createClusterMarker = (group: MapMarkerItem[], label: string) => {
        const longitude = group.reduce((sum, marker) => sum + marker.longitude, 0) / group.length;
        const latitude = group.reduce((sum, marker) => sum + marker.latitude, 0) / group.length;
        const priced = group.filter((marker) => marker.primaryPrice !== null)
            .sort((a, b) => (a.primaryPrice ?? 0) - (b.primaryPrice ?? 0));
        const median = priced[Math.floor(priced.length / 2)];
        const element = document.createElement("button");
        element.type = "button";
        element.className = `region-cluster ${mode === "PAST" ? "past" : "current"}`;
        element.setAttribute("aria-label", `${label} ${group.length}개 단지 확대`);
        const district = document.createElement("span");
        district.textContent = label;
        const count = document.createElement("strong");
        count.textContent = `${group.length}`;
        const summary = document.createElement("small");
        summary.textContent = median ? `중앙 ${priceLabel(median)}` : "거래 확인 전";
        element.append(district, count, summary);
        element.addEventListener("click", () => {
          map.setCenter(new naver.maps.LatLng(latitude, longitude));
          map.setZoom(Math.min(15, map.getZoom() + 2), true);
        });
        return new naver.maps.Marker({
          map,
          position: new naver.maps.LatLng(latitude, longitude),
          icon: { content: element, anchor: naver.maps.Position.CENTER },
          clickable: true,
          zIndex: 20,
        });
      };

      const createApartmentMarker = (marker: MapMarkerItem) => {
        const element = document.createElement("button");
        element.type = "button";
        const selected = marker.id === selectedId;
        element.className = [
          "estate-marker",
          mode === "PAST" ? "past" : "current",
          marker.eligible ? "eligible" : "",
          selected ? "selected" : "",
          marker.primaryPrice === null ? "unpriced" : "",
          marker.confidence === "LOW" ? "low-confidence" : "",
        ].filter(Boolean).join(" ");
        element.setAttribute("aria-label", `${marker.name}, ${priceLabel(marker)} 선택`);
        const dot = document.createElement("i");
        dot.className = "marker-status-dot";
        dot.setAttribute("aria-hidden", "true");
        const copy = document.createElement("span");
        copy.className = "marker-copy";
        const name = document.createElement("span");
        name.className = "marker-name";
        name.textContent = marker.name;
        const price = document.createElement("strong");
        price.textContent = priceLabel(marker);
        copy.append(name, price);
        element.append(dot, copy);
        const change = marker.changeRatePercent;
        if (mode === "PAST" && change !== null) {
          const changeLabel = document.createElement("small");
          changeLabel.className = change >= 0 ? "up" : "down";
          changeLabel.textContent = `${change >= 0 ? "+" : ""}${change.toFixed(1)}%`;
          element.append(changeLabel);
        }
        element.addEventListener("click", () => onSelectRef.current(marker.id));
        return new naver.maps.Marker({
          map,
          position: new naver.maps.LatLng(marker.latitude, marker.longitude),
          icon: { content: element, anchor: naver.maps.Position.BOTTOM_CENTER },
          clickable: true,
          zIndex: selected ? 30 : marker.primaryPrice === null ? 5 : 10,
        });
      };

      if (map.getZoom() < CLUSTER_ZOOM && markers.length > 1) {
        mapMarkersRef.current = [createClusterMarker(markers, "노원구")];
        return;
      }

      if (map.getZoom() < 14 && markers.length > 12) {
        const cellSize = map.getZoom() < 13 ? 0.018 : 0.009;
        const groups = new Map<string, MapMarkerItem[]>();
        markers.forEach((marker) => {
          const key = `${Math.floor(marker.longitude / cellSize)}:${Math.floor(marker.latitude / cellSize)}`;
          groups.set(key, [...(groups.get(key) ?? []), marker]);
        });
        mapMarkersRef.current = [...groups.values()].map((group) => group.length > 1
          ? createClusterMarker(group, "주변")
          : createApartmentMarker(group[0]));
        return;
      }

      mapMarkersRef.current = markers.map(createApartmentMarker);
    };

    renderMarkers();
    const zoomListener = naver.maps.Event.addListener(map, "zoom_changed", renderMarkers);
    return () => naver.maps.Event.removeListener(zoomListener);
  }, [mapReady, markers, mode, selectedId]);

  return <div className="real-estate-map-shell">
    <div className="real-estate-map" ref={containerRef} aria-label="네이버 노원구 아파트 실거래가 지도" />
    {!mapReady && !loadError && !disabledReason ? <div className="map-stage-loading">네이버 지도를 불러오는 중…</div> : null}
    {disabledReason ? <div className="map-provider-disabled" role="status"><strong>지도 없이 보는 중</strong><span>{disabledReason} 노원구 전체 범위로 목록을 보여드려요.</span></div> : null}
    {!disabledReason && loadError ? <div className="map-provider-error" role="alert"><strong>지도를 표시할 수 없습니다</strong><span>{loadError}</span></div> : null}
  </div>;
}
