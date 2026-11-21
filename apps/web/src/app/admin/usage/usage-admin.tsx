"use client";

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { ExternalLink, RefreshCw } from "lucide-react";
import { AdminAuthError, getUsageReport, syncUsageNow } from "@/lib/api";
import type { DailyUsage, UsageReport } from "@/lib/types";
import styles from "./page.module.css";

const TOKEN_KEY = "housing-lab-admin-token";
const DAYS = 30;
const SOURCE_LABELS: Record<string, string> = { mac: "Mac", minipc: "미니PC", local: "로컬" };

// 토큰은 이 브라우저의 localStorage 에만 둔다. 저장이 막혀 있으면 이번 화면에서만 쓴다.
let memoryToken = "";
const tokenListeners = new Set<() => void>();

function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? memoryToken;
  } catch {
    return memoryToken;
  }
}

function saveToken(token: string) {
  memoryToken = token;
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // memoryToken 으로 대신한다.
  }
  tokenListeners.forEach((listener) => listener());
}

function subscribeToken(listener: () => void) {
  tokenListeners.add(listener);
  return () => { tokenListeners.delete(listener); };
}

const seoulDate = (date: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(date);
const formatTime = (iso: string) => new Date(iso).toLocaleString("ko-KR", { timeZone: "Asia/Seoul", month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
const sourceLabel = (source: string) => SOURCE_LABELS[source] ?? source;

/** "0 50 23 * * *" → "매일 23:50". 그 밖의 형식은 그대로 보여준다. */
function describeCron(cron: string | null) {
  const parts = cron?.trim().split(/\s+/) ?? [];
  if (parts.length === 6 && parts.slice(3).every((part) => part === "*") && /^\d+$/.test(parts[1]) && /^\d+$/.test(parts[2])) {
    return `매일 ${parts[2].padStart(2, "0")}:${parts[1].padStart(2, "0")}`;
  }
  return cron ?? "";
}

function sum(rows: DailyUsage[]) {
  return rows.reduce((total, row) => ({ calls: total.calls + row.calls, errors: total.errors + row.errors }), { calls: 0, errors: 0 });
}

export default function UsageAdmin() {
  // 서버 렌더링에서는 null 이라 아무것도 그리지 않는다.
  const token = useSyncExternalStore(subscribeToken, readToken, () => null);
  const [draft, setDraft] = useState("");
  const [data, setData] = useState<{ report: UsageReport; fetchedAt: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const report = data?.report ?? null;

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getUsageReport(token, DAYS)
      .then((next) => {
        if (cancelled) return;
        setData({ report: next, fetchedAt: Date.now() });
        setError(null);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof AdminAuthError) {
          setData(null);
          saveToken("");
        }
        setError(err instanceof Error ? err.message : "사용량을 불러오지 못했습니다.");
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token, reloadKey]);

  const reload = () => {
    setLoading(true);
    setReloadKey((key) => key + 1);
  };

  const syncNow = async () => {
    if (!token) return;
    setSyncing(true);
    try {
      await syncUsageNow(token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "동기화하지 못했습니다.");
    } finally {
      setSyncing(false);
      reload();
    }
  };

  const fetchedAt = data?.fetchedAt ?? 0;
  const today = seoulDate(new Date(fetchedAt));
  const dates = useMemo(() => Array.from({ length: DAYS }, (_, i) => seoulDate(new Date(fetchedAt - i * 86_400_000))), [fetchedAt]);
  const byKey = useMemo(() => {
    const map = new Map<string, DailyUsage[]>();
    report?.days.forEach((row) => {
      const key = `${row.date}|${row.provider}`;
      map.set(key, [...(map.get(key) ?? []), row]);
    });
    return map;
  }, [report]);

  if (token === null) return null;

  if (!token) {
    return <main className={styles.page}>
      <form className={styles.login} onSubmit={(event) => {
        event.preventDefault();
        const value = draft.trim();
        if (!value) return;
        setLoading(true);
        saveToken(value);
      }}>
        <h1>외부 API 사용량</h1>
        <p>서버의 <code>ADMIN_TOKEN</code> 값을 입력하세요. 이 브라우저에만 저장됩니다.</p>
        <input type="password" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="관리자 토큰" autoFocus />
        <button type="submit">확인</button>
        {error && <p className={styles.error}>{error}</p>}
      </form>
    </main>;
  }

  const sync = report?.sync;
  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <h1>외부 API 사용량</h1>
        {report && <p>이 기기: <strong>{sourceLabel(report.source)}</strong> · 최근 {DAYS}일 · 날짜는 한국 시간 기준</p>}
      </div>
      <div className={styles.headerActions}>
        <button type="button" className={styles.ghost} onClick={reload} disabled={loading}>
          <RefreshCw size={16} className={loading ? styles.spin : undefined} /> 새로고침
        </button>
        <button type="button" className={styles.ghost} onClick={() => { saveToken(""); setData(null); setError(null); }}>로그아웃</button>
      </div>
    </header>

    {error && <p className={styles.error}>{error}</p>}

    {sync && <section className={styles.sync}>
      <h2>최근 동기화</h2>
      {sync.sender ? <>
        <p>
          {sync.lastAttempt
            ? <>
              <span className={sync.lastAttempt.success ? styles.ok : styles.fail}>{sync.lastAttempt.success ? "성공" : "실패"}</span>
              {" "}{formatTime(sync.lastAttempt.attemptedAt)}{sync.lastAttempt.manual ? " (수동)" : " (자동)"}
              {sync.lastAttempt.message && <small> · {sync.lastAttempt.message}</small>}
            </>
            : "아직 동기화한 적이 없습니다."}
        </p>
        <p className={styles.muted}>{describeCron(sync.schedule)} 자동으로 <code>{sync.target}</code> 에 보냅니다. 실패하면 다음 날 다시 보냅니다.</p>
        <button type="button" className={styles.primary} onClick={syncNow} disabled={syncing}>
          <RefreshCw size={16} className={syncing ? styles.spin : undefined} /> {syncing ? "보내는 중…" : "지금 동기화"}
        </button>
      </> : <>
        {sync.received.length > 0
          ? <ul className={styles.received}>
            {sync.received.map((item) => <li key={item.source}><strong>{sourceLabel(item.source)}</strong> 마지막 수신 {formatTime(item.receivedAt)} · {item.rowCount}행</li>)}
          </ul>
          : <p>아직 다른 기기에서 받은 기록이 없습니다.</p>}
        <p className={styles.muted}>이 기기는 받는 쪽입니다. 수동 동기화는 Mac 관리자 페이지에서 할 수 있어요.</p>
      </>}
    </section>}

    {report && <>
      <section className={styles.cards}>
        {report.providers.map((provider) => {
          const rows = byKey.get(`${today}|${provider.id}`) ?? [];
          const total = sum(rows);
          const ratio = provider.dailyLimit > 0 ? Math.min(1, total.calls / provider.dailyLimit) : 0;
          return <article key={provider.id} className={styles.card}>
            <header>
              <h3>{provider.label}</h3>
              <a href={provider.consoleUrl} target="_blank" rel="noreferrer" title="기관 콘솔에서 확인">콘솔 <ExternalLink size={13} /></a>
            </header>
            <p className={styles.big}>{total.calls.toLocaleString("ko-KR")}<small>회 오늘</small></p>
            {provider.dailyLimit > 0
              ? <>
                <div className={styles.meter}><span style={{ width: `${ratio * 100}%` }} data-level={ratio >= 0.9 ? "high" : ratio >= 0.7 ? "mid" : "low"} /></div>
                <p className={styles.muted}>일일 한도 {provider.dailyLimit.toLocaleString("ko-KR")}회 중 {Math.round(ratio * 100)}%</p>
              </>
              : <p className={styles.muted}>한도 미설정 (USAGE_LIMIT_{provider.id})</p>}
            <p className={styles.muted}>
              {rows.length > 0 ? rows.map((row) => `${sourceLabel(row.source)} ${row.calls.toLocaleString("ko-KR")}`).join(" · ") : "기록 없음"}
              {total.errors > 0 && <span className={styles.fail}> · 오류 {total.errors}</span>}
            </p>
          </article>;
        })}
      </section>

      <section className={styles.tableWrap}>
        <h2>일별 호출 수</h2>
        <table className={styles.table}>
          <thead>
            <tr><th>날짜</th>{report.providers.map((provider) => <th key={provider.id}>{provider.label}</th>)}</tr>
          </thead>
          <tbody>
            {dates.map((date) => <tr key={date}>
              <td>{date}</td>
              {report.providers.map((provider) => {
                const rows = byKey.get(`${date}|${provider.id}`) ?? [];
                const total = sum(rows);
                return <td key={provider.id} title={rows.map((row) => `${sourceLabel(row.source)}: ${row.calls}회 (오류 ${row.errors})`).join("\n")}>
                  {total.calls > 0 ? total.calls.toLocaleString("ko-KR") : <span className={styles.muted}>-</span>}
                  {total.errors > 0 && <small className={styles.fail}> ({total.errors})</small>}
                </td>;
              })}
            </tr>)}
          </tbody>
        </table>
        <p className={styles.muted}>괄호는 오류 수입니다. 칸에 마우스를 올리면 기기별 내역이 보입니다.</p>
      </section>
    </>}
  </main>;
}
