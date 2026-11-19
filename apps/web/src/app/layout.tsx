import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "우리 살 집 | 우리 가족, 어디서 살까?",
  description: "집은 꼭 필요하니까. 지금 우리 동네 집값과, 그때 이 돈이면 살 수 있었던 집을 지도에서 함께 살펴봐요.",
  openGraph: {
    title: "우리 살 집",
    description: "우리 가족, 어디서 살까? 실제 거래 기록으로 함께 살펴봐요.",
    locale: "ko_KR",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#ffffff",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return <html lang="ko">
    <head>
      <link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="anonymous" />
      <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css" />
    </head>
    <body>{children}</body>
  </html>;
}
