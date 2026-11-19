"use client";

import {
  AttributionControl,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  setWorkerUrl,
} from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { useEffect, useRef } from "react";
import type { Candidate } from "@/lib/types";

setWorkerUrl("/maplibre-gl-worker.mjs");

const BASE_MAP_STYLE = "https://tiles.openfreemap.org/styles/positron";

type OpportunityMapProps = {
  candidates: Candidate[];
  selectedId: string | null;
  onSelect: (id: string) => void;
};

export default function OpportunityMap({
  candidates,
  selectedId,
  onSelect,
}: OpportunityMapProps) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<MapLibreMap | null>(null);
  const markers = useRef<Marker[]>([]);

  useEffect(() => {
    if (!container.current || map.current) return;

    map.current = new MapLibreMap({
      container: container.current,
      style: BASE_MAP_STYLE,
      center: [127.066, 37.65],
      zoom: 12,
      attributionControl: false,
    });
    map.current.addControl(
      new NavigationControl({ showCompass: false }),
      "bottom-right",
    );
    map.current.addControl(new AttributionControl({ compact: true }));

    return () => {
      map.current?.remove();
      map.current = null;
    };
  }, []);

  useEffect(() => {
    if (!map.current) return;

    markers.current.forEach((marker) => marker.remove());
    markers.current = candidates.map((candidate) => {
      const element = document.createElement("button");
      element.className = `map-price-marker ${
        candidate.building.id === selectedId ? "active" : ""
      }`;
      element.type = "button";
      element.textContent = `${Math.round(candidate.historicalPriceRange.median / 10_000_000) / 10}억`;
      element.setAttribute("aria-label", `${candidate.building.name} 선택`);
      element.addEventListener("click", () => onSelect(candidate.building.id));

      return new Marker({ element })
        .setLngLat([
          candidate.building.longitude,
          candidate.building.latitude,
        ])
        .addTo(map.current!);
    });
  }, [candidates, selectedId, onSelect]);

  return (
    <div
      className="map-canvas"
      ref={container}
      aria-label="거래 관측 후보 지도"
    />
  );
}
