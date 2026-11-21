import type { Metadata } from "next";
import UsageAdmin from "./usage-admin";

export const metadata: Metadata = {
  title: "외부 API 사용량 | 우리 살 집",
  robots: { index: false, follow: false },
};

export default function UsageAdminPage() {
  return <UsageAdmin />;
}
