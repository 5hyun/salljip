-- 지도 API가 요청마다 계산하는 MAX(data_as_of)를 전체 스캔 대신 인덱스 한 번으로 끝낸다.
CREATE INDEX idx_trades_data_as_of ON trades(data_as_of);
