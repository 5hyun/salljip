import type { NextConfig } from "next";

// 브라우저는 같은 주소의 /api/* 를 부르고, Next 서버가 내부 API 로 전달한다 (외부엔 웹 포트 하나만 노출)
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:18080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiInternalUrl}/api/:path*` }];
  },
};

export default nextConfig;
