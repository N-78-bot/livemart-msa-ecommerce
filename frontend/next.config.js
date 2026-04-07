/** @type {import('next').NextConfig} */
const nextConfig = {
  output: process.env.VERCEL ? undefined : 'standalone',
  reactStrictMode: true,

  // ── 이미지 최적화 ─────────────────────────────────────────
  images: {
    formats: ['image/avif', 'image/webp'],
    deviceSizes: [640, 750, 828, 1080, 1200, 1920],
    imageSizes: [16, 32, 48, 64, 96, 128, 256],
    minimumCacheTTL: 86400, // 24h
    remotePatterns: [
      { protocol: 'https', hostname: 'cdn.livemart.com' },
      { protocol: 'https', hostname: 'images.unsplash.com' },
      { protocol: 'https', hostname: '*.vercel.app' },
      { protocol: 'http',  hostname: 'localhost' },
    ],
  },

  // ── 프로덕션 console.log 제거 ─────────────────────────────
  compiler: {
    removeConsole: process.env.NODE_ENV === 'production'
      ? { exclude: ['error', 'warn'] }
      : false,
  },

  // ── 보안 · 성능 HTTP 헤더 ─────────────────────────────────
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Content-Type-Options',   value: 'nosniff' },
          { key: 'X-Frame-Options',           value: 'SAMEORIGIN' },
          { key: 'X-XSS-Protection',          value: '1; mode=block' },
          { key: 'Referrer-Policy',           value: 'strict-origin-when-cross-origin' },
          { key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' },
          { key: 'Permissions-Policy',        value: 'camera=(), microphone=(), geolocation=(), interest-cohort=()' },
          {
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              // Next.js는 런타임에 inline script/style을 사용하므로 nonce 기반이 이상적이나,
              // App Router 정적 생성 환경에서 unsafe-inline은 필수. unsafe-eval은 제거.
              "script-src 'self' 'unsafe-inline' https://js.tosspayments.com https://t1.daumcdn.net",
              "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
              "font-src 'self' https://fonts.gstatic.com",
              "img-src 'self' data: blob: https://cdn.livemart.com http://localhost:* https:",
              "connect-src 'self' http://localhost:* ws://localhost:* http://34.64.189.54:8888 https://api.livemart.com https://*.tosspayments.com https://t1.daumcdn.net",
              "frame-src https://js.tosspayments.com https://pay.toss.im https://postcode.map.daum.net",
              "frame-ancestors 'self'",
              "base-uri 'self'",
              "form-action 'self'",
              // upgrade-insecure-requests 제거: GCP VM 백엔드가 HTTP이므로 강제 업그레이드 시 차단됨
            ].join('; '),
          },
        ],
      },
      // 정적 자산 장기 캐싱 (immutable)
      {
        source: '/_next/static/(.*)',
        headers: [
          { key: 'Cache-Control', value: 'public, max-age=31536000, immutable' },
        ],
      },
    ];
  },

  // ── API 프록시 리라이트 ───────────────────────────────────
  async rewrites() {
    const GCP_API = 'http://34.64.189.54:8888';

    let apiBase = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || GCP_API;

    // Railway/Render 내부 호스트명(.internal, localhost)은 Vercel에서 private IP로 해석됨
    // → 이 경우 GCP VM으로 강제 전환
    const isPrivateHost = /localhost|127\.0\.0\.1|\.internal/.test(apiBase);
    if (isPrivateHost) apiBase = GCP_API;

    if (!apiBase) return [];

    // SSE 스트리밍은 notification-service 직접 연결 (API Gateway kubectl port-forward 연결 끊김 방지)
    const notifBase = process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:8086';
    return [
      { source: '/oauth2/:path*', destination: `${apiBase}/oauth2/:path*` },
      { source: '/login/:path*',  destination: `${apiBase}/login/:path*`  },
      // SSE 엔드포인트는 게이트웨이 우회, notification-service 직접 연결
      { source: '/api/notifications/stream/:uid', destination: `${notifBase}/api/notifications/stream/:uid` },
      { source: '/api/:path*',    destination: `${apiBase}/api/:path*`    },
    ];
  },

};

module.exports = nextConfig;
