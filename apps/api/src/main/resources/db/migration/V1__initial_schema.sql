CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE regions (
  region_code VARCHAR(10) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  parent_code VARCHAR(10),
  boundary_geom geometry(MultiPolygon, 4326)
);

CREATE TABLE buildings (
  id UUID PRIMARY KEY,
  region_code VARCHAR(10) NOT NULL REFERENCES regions(region_code),
  canonical_name VARCHAR(200) NOT NULL,
  addr_main VARCHAR(30) NOT NULL,
  addr_sub VARCHAR(30),
  location geometry(Point, 4326) NOT NULL,
  approved_at DATE
);
CREATE INDEX idx_buildings_region ON buildings(region_code);
CREATE INDEX idx_buildings_location ON buildings USING GIST(location);

CREATE TABLE building_source_keys (
  id UUID PRIMARY KEY,
  building_id UUID NOT NULL REFERENCES buildings(id),
  provider VARCHAR(50) NOT NULL,
  provider_key VARCHAR(200) NOT NULL,
  confidence NUMERIC(5,4) NOT NULL,
  verified_at TIMESTAMPTZ,
  UNIQUE(provider, provider_key)
);

CREATE TABLE raw_trade_records (
  id UUID PRIMARY KEY,
  provider VARCHAR(50) NOT NULL,
  region_code VARCHAR(10) NOT NULL,
  contract_month VARCHAR(6) NOT NULL,
  payload_json JSONB NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  fetched_at TIMESTAMPTZ NOT NULL,
  batch_id UUID NOT NULL,
  UNIQUE(provider, region_code, contract_month, content_hash)
);

CREATE TABLE trades (
  id UUID PRIMARY KEY,
  source_fingerprint VARCHAR(128) NOT NULL,
  building_id UUID REFERENCES buildings(id),
  region_code VARCHAR(10) NOT NULL REFERENCES regions(region_code),
  contract_date DATE NOT NULL,
  floor INTEGER,
  exclusive_area_m2 NUMERIC(8,2) NOT NULL,
  price_krw BIGINT NOT NULL CHECK (price_krw > 0),
  trade_type VARCHAR(20) NOT NULL,
  canceled_at TIMESTAMPTZ,
  source_version INTEGER NOT NULL DEFAULT 1,
  data_as_of TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_trade_source_version ON trades(source_fingerprint, source_version);
CREATE INDEX idx_trades_building_date ON trades(building_id, contract_date DESC);
CREATE INDEX idx_trades_search ON trades(region_code, contract_date, price_krw);

CREATE TABLE trade_versions (
  trade_id UUID NOT NULL REFERENCES trades(id),
  version INTEGER NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  operation VARCHAR(20) NOT NULL,
  raw_id UUID REFERENCES raw_trade_records(id),
  PRIMARY KEY(trade_id, version)
);

CREATE TABLE asset_observations (
  asset_key VARCHAR(50) NOT NULL,
  observation_date DATE NOT NULL,
  value NUMERIC(24,8) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  price_kind VARCHAR(30) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY(asset_key, observation_date, source_id, version)
);

CREATE TABLE fx_observations (
  base VARCHAR(3) NOT NULL,
  quote VARCHAR(3) NOT NULL,
  observation_date DATE NOT NULL,
  rate NUMERIC(20,8) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  PRIMARY KEY(base, quote, observation_date, source_id)
);

CREATE TABLE market_indicators (
  code VARCHAR(80) NOT NULL,
  region_code VARCHAR(10) NOT NULL,
  period_start DATE NOT NULL,
  release_date DATE NOT NULL,
  value NUMERIC(20,6) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY(code, region_code, period_start, source_id, version)
);

CREATE TABLE simulation_runs (
  id UUID PRIMARY KEY,
  input_hash VARCHAR(64) NOT NULL,
  assumption_json JSONB NOT NULL,
  engine_version VARCHAR(30) NOT NULL,
  data_as_of TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  output_json JSONB NOT NULL
);
CREATE INDEX idx_simulation_reproducible ON simulation_runs(input_hash, engine_version);

CREATE TABLE ingestion_runs (
  id UUID PRIMARY KEY,
  dataset_id VARCHAR(100) NOT NULL,
  region_code VARCHAR(10) NOT NULL,
  period VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  cursor_value VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,
  retry_count INTEGER NOT NULL DEFAULT 0,
  run_generation INTEGER NOT NULL DEFAULT 1,
  UNIQUE(dataset_id, region_code, period, run_generation)
);

CREATE TABLE source_licenses (
  source_id VARCHAR(100) PRIMARY KEY,
  provider VARCHAR(100) NOT NULL,
  dataset VARCHAR(200) NOT NULL,
  permission VARCHAR(30) NOT NULL,
  allowed_public BOOLEAN NOT NULL DEFAULT FALSE,
  allowed_commercial BOOLEAN NOT NULL DEFAULT FALSE,
  attribution TEXT,
  license_url TEXT,
  reviewed_at DATE,
  notes TEXT
);
