/**
 * 홈 페이지 — Next.js 15 Server Component
 * Editorial commerce · Warm cream palette · Bebas Neue accents
 */

import { GlobalNav } from '@/components/GlobalNav';
import { ProductCard } from '@/components/ProductCard';
import { CartSummary } from '@/components/CartSummary';
import { HeroBanner } from './_components/HeroBanner.client';
import { FlashSaleSection } from './_components/FlashSaleSection.client';
import { LiveActivityBar } from './_components/LiveActivityBar.client';
import { WelcomeCouponBanner } from './_components/WelcomeCouponBanner.client';
import { RecentlyViewedSection } from './_components/RecentlyViewedSection.client';
import { AiRecommendations } from './_components/AiRecommendations.client';

interface Product {
  id: number;
  name: string;
  price: number;
  imageUrl?: string;
  stockQuantity?: number;
  categoryId?: number;
  description?: string;
}

const CATEGORIES = [
  {
    label: '전자기기', href: '/products?cat=1',
    accent: '#2563EB', bg: 'rgba(37,99,235,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
      </svg>
    ),
  },
  {
    label: '패션', href: '/products?cat=2',
    accent: '#DB2777', bg: 'rgba(219,39,119,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 2l1.5 4.5S6 8 6 10v10h12V10c0-2-1.5-3.5-1.5-3.5L18 2c-1.5 1-3 1.5-6 1.5S7.5 3 6 2z"/>
      </svg>
    ),
  },
  {
    label: '식품', href: '/products?cat=3',
    accent: '#16A34A', bg: 'rgba(22,163,74,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
      </svg>
    ),
  },
  {
    label: '홈/리빙', href: '/products?cat=4',
    accent: '#B45309', bg: 'rgba(180,83,9,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
      </svg>
    ),
  },
  {
    label: '뷰티', href: '/products?cat=5',
    accent: '#DB2777', bg: 'rgba(219,39,119,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 3c-4.5 0-7 3-7 6 0 2.5 1.5 4.5 3.5 5.5V18a1 1 0 001 1h5a1 1 0 001-1v-3.5C17.5 13.5 19 11.5 19 9c0-3-2.5-6-7-6z M9 21h6"/>
      </svg>
    ),
  },
  {
    label: '스포츠', href: '/products?cat=6',
    accent: '#EA580C', bg: 'rgba(234,88,12,0.08)',
    icon: (
      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M13 10V3L4 14h7v7l9-11h-7z"/>
      </svg>
    ),
  },
];

async function fetchProducts(): Promise<Product[]> {
  // 서버 컴포넌트는 상대경로 불가 → API_GATEWAY_URL(서버 전용) 또는 NEXT_PUBLIC_API_URL 사용
  // 주의: VERCEL_URL은 Vercel 자체 도메인이므로 API URL로 사용하면 안 됨
  const GCP_API = process.env.GCP_API_URL || '';
  let apiBase = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || GCP_API;
  if (/localhost|127\.0\.0\.1|\.internal/.test(apiBase)) apiBase = GCP_API;
  try {
    const res = await fetch(`${apiBase}/api/products?page=0&size=20`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data?.content ?? data ?? [];
  } catch {
    return [];
  }
}

// ── 섹션 헤더 — editorial style ─────────────────────────────
function SectionHeader({
  title,
  subtitle,
  href,
  eyebrow,
}: {
  title: string;
  subtitle?: string;
  href?: string;
  eyebrow?: string;
}) {
  return (
    <div className="flex items-end justify-between mb-6">
      <div>
        {eyebrow && (
          <div
            className="text-[10px] font-black uppercase tracking-[0.22em] mb-2"
            style={{ color: '#E8001D' }}
          >
            {eyebrow}
          </div>
        )}
        <div className="flex items-baseline gap-3">
          <h2
            className="font-bebas tracking-wide leading-none"
            style={{ fontSize: 'clamp(1.4rem, 3vw, 1.9rem)', color: '#0E0E0E', letterSpacing: '0.03em' }}
          >
            {title}
          </h2>
          {subtitle && (
            <span className="text-sm font-normal hidden sm:block" style={{ color: 'rgba(14,14,14,0.35)' }}>
              {subtitle}
            </span>
          )}
        </div>
      </div>
      {href && (
        <a
          href={href}
          className="flex items-center gap-1 text-[12px] font-semibold uppercase tracking-wider pb-0.5 transition-colors flex-shrink-0 ml-4"
          style={{
            color: 'rgba(14,14,14,0.35)',
            borderBottom: '1px solid rgba(14,14,14,0.15)',
          }}
        >
          전체보기
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </a>
      )}
    </div>
  );
}


export default async function Home() {
  const products = await fetchProducts();

  const electronics = products.filter(p => p.categoryId === 1).slice(0, 5);
  const fashion = products.filter(p => p.categoryId === 2).slice(0, 5);
  const featured = products.slice(0, 10);
  const newArrivals = [...products].reverse().slice(0, 10);

  return (
    <main className="min-h-screen pb-14 md:pb-0" style={{ background: 'var(--bg)' }}>
      <GlobalNav />

      {/* Hero — full viewport width */}
      <HeroBanner />

      {/* Flash Sale — dark section */}
      <FlashSaleSection products={products} />

      {/* 실시간 접속자 현황 바 */}
      <LiveActivityBar />

      {/* ── 메인 콘텐츠 ── */}
      <div className="max-w-[1440px] mx-auto px-4 lg:px-8 xl:px-12">

        {/* ── 혜택 스트립 ── */}
        <div style={{ paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
          <div
            className="grid grid-cols-1 sm:grid-cols-3"
            style={{ border: '1px solid rgba(14,14,14,0.08)' }}
          >
            {[
              {
                icon: (
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0" />
                  </svg>
                ),
                accent: '#2563EB',
                title: '빠른 배송',
                desc: '빠른 배송 · 실시간 조회 지원',
              },
              {
                icon: (
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                  </svg>
                ),
                accent: '#059669',
                title: '5만원 이상 무료',
                desc: '전 품목 무료배송 혜택',
              },
              {
                icon: (
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                  </svg>
                ),
                accent: '#7C3AED',
                title: '안전결제',
                desc: '카드 · 계좌 · 간편결제',
              },
            ].map((item, i) => (
              <div
                key={item.title}
                className="flex items-center gap-4 px-6 py-5"
                style={{
                  background: '#fff',
                  borderRight: i < 2 ? '1px solid rgba(14,14,14,0.06)' : 'none',
                }}
              >
                <div
                  className="w-10 h-10 flex items-center justify-center flex-shrink-0"
                  style={{ background: `${item.accent}12`, color: item.accent }}
                >
                  {item.icon}
                </div>
                <div>
                  <div className="font-bold text-sm leading-tight" style={{ color: '#0E0E0E' }}>{item.title}</div>
                  <div className="text-xs mt-0.5" style={{ color: 'rgba(14,14,14,0.4)' }}>{item.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ── 카테고리 ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
          <SectionHeader title="카테고리" eyebrow="Browse" subtitle="원하는 상품을 빠르게" />
          <div className="grid grid-cols-3 sm:grid-cols-6 gap-3 md:gap-4">
            {CATEGORIES.map(cat => (
              <a
                key={cat.label}
                href={cat.href}
                className="flex flex-col items-center gap-2.5 group py-1"
              >
                <div
                  className="w-14 h-14 lg:w-16 lg:h-16 flex items-center justify-center transition-all duration-200 group-hover:scale-105"
                  style={{
                    background: cat.bg,
                    color: cat.accent,
                  }}
                >
                  {cat.icon}
                </div>
                <span
                  className="text-xs font-semibold text-center leading-tight transition-colors"
                  style={{ color: 'rgba(14,14,14,0.6)' }}
                >
                  {cat.label}
                </span>
              </a>
            ))}
          </div>
        </div>

        {/* ── 서비스 특징 ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
          <SectionHeader title="LiveMart" eyebrow="Features" subtitle="직접 구현한 핵심 기능" />
          <div
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-px"
            style={{ background: 'rgba(14,14,14,0.07)', border: '1px solid rgba(14,14,14,0.07)' }}
          >
            {[
              {
                accent: '#E8001D',
                eyebrow: '분산 트랜잭션',
                title: '실시간 주문 처리',
                desc: 'Kafka + Saga Choreography 패턴으로 주문·결제·재고를 분산 트랜잭션으로 안전하게 처리합니다.',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"/>
                ),
              },
              {
                accent: '#0EA5E9',
                eyebrow: '스마트 검색',
                title: 'Elasticsearch 검색',
                desc: 'Elasticsearch + nori 형태소 분석기로 한국어 자연어 검색을 지원하고 Redis 캐싱으로 응답 속도를 높였습니다.',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                ),
              },
              {
                accent: '#8B5CF6',
                eyebrow: '안전 결제',
                title: 'Stripe 결제 연동',
                desc: 'Stripe 결제 SDK와 멱등성 키 기반 중복 방지로 안전하고 신뢰할 수 있는 결제 처리를 구현했습니다.',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/>
                ),
              },
              {
                accent: '#22C55E',
                eyebrow: 'AI 추천',
                title: '맞춤 상품 추천',
                desc: 'Spring AI 1.0 + OpenRouter LLM 연동으로 구매 이력 기반 개인화 추천을 제공하고 Redis로 캐싱합니다.',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/>
                ),
              },
            ].map((item) => (
              <div
                key={item.title}
                className="flex flex-col p-6 md:p-7"
                style={{ background: '#fff' }}
              >
                <div
                  className="w-10 h-10 flex items-center justify-center mb-5 flex-shrink-0"
                  style={{ background: `${item.accent}12`, color: item.accent }}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    {item.icon}
                  </svg>
                </div>
                <div
                  className="text-[10px] font-black uppercase tracking-[0.18em] mb-2"
                  style={{ color: item.accent }}
                >
                  {item.eyebrow}
                </div>
                <h3 className="font-bold text-sm mb-3 leading-tight" style={{ color: '#0E0E0E' }}>
                  {item.title}
                </h3>
                <p className="text-xs leading-relaxed" style={{ color: 'rgba(14,14,14,0.42)', wordBreak: 'keep-all' }}>
                  {item.desc}
                </p>
              </div>
            ))}
          </div>
        </div>

        {/* ── AI 추천 ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)' }}>
          <AiRecommendations />
        </div>

        {/* ── MD PICK editorial block ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
          <SectionHeader title="에디터 추천" eyebrow="MD Pick" />
          <div style={{ border: '1px solid rgba(14,14,14,0.08)' }}>
            {/* 상단 타이틀바 */}
            <div
              className="flex items-center gap-3 px-5 py-3"
              style={{ borderBottom: '1px solid rgba(14,14,14,0.07)', background: '#fff' }}
            >
              <div
                className="px-2.5 py-1 text-[10px] font-black uppercase tracking-widest text-white"
                style={{ background: '#0E0E0E' }}
              >
                MD PICK
              </div>
              <span className="text-xs" style={{ color: 'rgba(14,14,14,0.4)' }}>전문 MD가 엄선한 이달의 상품</span>
            </div>

            <div style={{ background: '#fff' }}>
              {[
                {
                  title: '2026 신형 스마트폰 특가',
                  desc: '최신 플래그십 모델 역대급 할인',
                  badge: '-25%',
                  badgeColor: '#E8001D',
                  href: '/products?cat=1',
                  num: '01',
                },
                {
                  title: '봄 시즌 신상 패션',
                  desc: '트렌디한 봄 패션 아이템 총집합',
                  badge: 'NEW',
                  badgeColor: '#059669',
                  href: '/products?cat=2',
                  num: '02',
                },
                {
                  title: '홈 인테리어 위크',
                  desc: '집을 더 예쁘게, 최대 40% 세일',
                  badge: '~40%',
                  badgeColor: '#B45309',
                  href: '/products?cat=4',
                  num: '03',
                },
              ].map((item, i) => (
                <a
                  key={item.title}
                  href={item.href}
                  className="flex items-center gap-5 px-5 py-4 group transition-colors hover:bg-[#FAFAF8]"
                  style={{
                    borderBottom: i < 2 ? '1px solid rgba(14,14,14,0.05)' : 'none',
                  }}
                >
                  <span
                    className="font-bebas tabular-nums leading-none flex-shrink-0 w-10"
                    style={{ fontSize: '2rem', color: 'rgba(14,14,14,0.07)', letterSpacing: '0.02em' }}
                  >
                    {item.num}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className="text-[10px] font-black px-1.5 py-0.5"
                        style={{ color: item.badgeColor, background: `${item.badgeColor}15` }}
                      >
                        {item.badge}
                      </span>
                    </div>
                    <h3
                      className="font-semibold text-sm line-clamp-1 transition-colors"
                      style={{ color: '#0E0E0E' }}
                    >
                      {item.title}
                    </h3>
                    <p className="text-xs mt-0.5" style={{ color: 'rgba(14,14,14,0.4)' }}>{item.desc}</p>
                  </div>
                  <svg
                    className="w-4 h-4 flex-shrink-0 transition-colors"
                    style={{ color: 'rgba(14,14,14,0.2)' }}
                    fill="none" stroke="currentColor" viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </a>
              ))}
            </div>
          </div>
        </div>

        {/* ── 오늘의 추천 — product grid ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
          <SectionHeader
            title="오늘의 추천"
            eyebrow="Featured"
            subtitle="가장 인기 있는 상품"
            href="/products"
          />
          {featured.length > 0 ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 xl:gap-5">
              {featured.map(product => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          ) : (
            <div className="py-16 flex flex-col items-center gap-3" style={{ color: 'rgba(14,14,14,0.2)' }}>
              <div
                className="w-14 h-14 flex items-center justify-center"
                style={{ background: 'rgba(14,14,14,0.05)' }}
              >
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              </div>
              <p className="text-sm font-medium">등록된 상품이 없습니다</p>
            </div>
          )}
        </div>

        {/* ── 전자기기 & 패션 — 2-column ── */}
        {(electronics.length > 0 || fashion.length > 0) && (
          <div
            className="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-10"
            style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}
          >
            {electronics.length > 0 && (
              <section>
                <SectionHeader title="전자기기" eyebrow="Electronics" href="/products?cat=1" />
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                  {electronics.map(product => <ProductCard key={product.id} product={product} />)}
                </div>
              </section>
            )}
            {fashion.length > 0 && (
              <section>
                <SectionHeader title="패션" eyebrow="Fashion" href="/products?cat=2" />
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                  {fashion.map(product => <ProductCard key={product.id} product={product} />)}
                </div>
              </section>
            )}
          </div>
        )}

        {/* ── 신상품 ── */}
        {newArrivals.length > 0 && (
          <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}>
            <SectionHeader
              title="신상품"
              eyebrow="New In"
              subtitle="방금 입고된 새로운 상품들"
              href="/products"
            />
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 xl:gap-5">
              {newArrivals.map(product => <ProductCard key={`new-${product.id}`} product={product} />)}
            </div>
          </div>
        )}

        {/* ── 최근 본 상품 ── */}
        <div style={{ borderTop: '1px solid rgba(14,14,14,0.07)' }}>
          <RecentlyViewedSection />
        </div>

        {/* ── 하단 배너 2-col ── */}
        <div
          className="grid grid-cols-1 sm:grid-cols-2 gap-4"
          style={{ borderTop: '1px solid rgba(14,14,14,0.07)', paddingTop: '2.5rem', paddingBottom: '2.5rem' }}
        >
          <WelcomeCouponBanner />
          <a
            href="/seller"
            className="group flex items-center justify-between px-7 py-6 text-white transition-colors hover:bg-[#161616]"
            style={{ background: '#0A0A0A' }}
          >
            <div>
              <div
                className="text-[10px] font-black uppercase tracking-[0.2em] mb-2"
                style={{ color: 'rgba(255,255,255,0.25)' }}
              >
                Seller Program
              </div>
              <div className="font-bold text-lg leading-tight mb-1">판매자 시작하기</div>
              <div className="text-sm" style={{ color: 'rgba(255,255,255,0.35)' }}>지금 바로 판매를 시작해보세요</div>
            </div>
            <div
              className="w-12 h-12 flex items-center justify-center flex-shrink-0 ml-4 transition-colors"
              style={{ border: '1px solid rgba(255,255,255,0.1)' }}
            >
              <svg className="w-5 h-5" style={{ color: 'rgba(255,255,255,0.35)' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                  d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
          </a>
        </div>
      </div>

      <CartSummary />

      {/* ── 푸터 ── */}
      <footer style={{ background: '#0A0A0A', color: '#fff', marginTop: '3rem' }}>
        <div className="max-w-[1440px] mx-auto px-4 lg:px-8 xl:px-12 py-12">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 lg:gap-12 mb-10">
            <div>
              <div className="mb-4">
                <span
                  className="font-bebas tracking-wider"
                  style={{ fontSize: '1.6rem', letterSpacing: '0.04em' }}
                >
                  <span style={{ color: '#E8001D' }}>LIVE</span>
                  <span style={{ color: '#fff' }}>MART</span>
                </span>
              </div>
              <p className="text-sm leading-relaxed" style={{ color: 'rgba(255,255,255,0.3)' }}>
                빠른 배송과 최저가로<br />만족스러운 쇼핑 경험을<br />제공합니다.
              </p>
            </div>
            {[
              {
                title: '쇼핑',
                links: [
                  { label: '전체 상품', href: '/products' },
                  { label: '검색', href: '/search' },
                  { label: '장바구니', href: '/cart' },
                  { label: '위시리스트', href: '/wishlist' },
                ],
              },
              {
                title: '고객센터',
                links: [
                  { label: '주문 내역', href: '/my-orders' },
                  { label: '반품/환불', href: '/returns' },
                  { label: '알림', href: '/notifications' },
                  { label: '내 정보', href: '/profile' },
                ],
              },
              {
                title: '비즈니스',
                links: [
                  { label: '판매자센터', href: '/seller' },
                  { label: '관리자', href: '/admin' },
                  { label: '상품 등록', href: '/seller/products' },
                  { label: '쿠폰 관리', href: '/admin/coupons' },
                ],
              },
            ].map(col => (
              <div key={col.title}>
                <h4
                  className="font-semibold mb-4 text-xs uppercase tracking-[0.15em]"
                  style={{ color: 'rgba(255,255,255,0.3)' }}
                >
                  {col.title}
                </h4>
                <ul className="space-y-2.5">
                  {col.links.map(link => (
                    <li key={link.label}>
                      <a
                        href={link.href}
                        className="text-sm transition-colors hover:!text-white/75"
                        style={{ color: 'rgba(255,255,255,0.35)' }}
                      >
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div
            className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-6"
            style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}
          >
            <p className="text-xs" style={{ color: 'rgba(255,255,255,0.2)' }}>© 2026 LiveMart. All rights reserved.</p>
            <div className="flex items-center gap-5 text-xs" style={{ color: 'rgba(255,255,255,0.2)' }}>
              <a href="#" className="transition-colors hover:text-white/50">이용약관</a>
              <a href="#" className="transition-colors hover:text-white/50">개인정보처리방침</a>
              <a href="#" className="transition-colors hover:text-white/50">고객센터</a>
            </div>
          </div>
        </div>
      </footer>
    </main>
  );
}
