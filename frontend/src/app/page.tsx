'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { GlobalNav } from '@/components/GlobalNav';
import { ProductCard } from '@/components/ProductCard';
import { CartSummary } from '@/components/CartSummary';
import { productApi } from '@/api/productApi';
import toast from 'react-hot-toast';

interface Product {
  id: number;
  name: string;
  price: number;
  imageUrl?: string;
  stockQuantity?: number;
  categoryId?: number;
  description?: string;
}

// ── 히어로 배너 데이터 (이모지 제거, 타이포그래피 기반) ──
const BANNERS = [
  {
    id: 1,
    tag: '봄 맞이 특가',
    title: '인기 상품\n최대 30% 할인',
    cta: '지금 쇼핑하기',
    href: '/products',
    bg: '#111827',
    accent: '#E11D48',
  },
  {
    id: 2,
    tag: '무료 배송 혜택',
    title: '5만원 이상\n무료배송',
    cta: '상품 보기',
    href: '/products',
    bg: '#0f2240',
    accent: '#3B82F6',
  },
  {
    id: 3,
    tag: '신상품 입고',
    title: '봄 시즌\n신상 컬렉션',
    cta: '신상품 보기',
    href: '/products',
    bg: '#1e1040',
    accent: '#7C3AED',
  },
];

// ── 카테고리 (모노그램 원형 아이콘) ──
const CATEGORIES = [
  { label: '전자기기', mono: '전', bg: 'bg-slate-800', href: '/search?cat=1' },
  { label: '패션', mono: '패', bg: 'bg-rose-500', href: '/search?cat=2' },
  { label: '식품', mono: '식', bg: 'bg-green-600', href: '/search?cat=3' },
  { label: '홈/리빙', mono: '홈', bg: 'bg-amber-500', href: '/search?cat=4' },
  { label: '뷰티', mono: '뷰', bg: 'bg-pink-500', href: '/search?cat=5' },
  { label: '스포츠', mono: '스', bg: 'bg-orange-500', href: '/search?cat=6' },
];

// ── 플래시세일 섹션 ──────────────────────────────────
function FlashSaleSection({ products }: { products: Product[] }) {
  const [timeLeft, setTimeLeft] = useState({ h: 0, m: 0, s: 0 });

  useEffect(() => {
    const now = new Date();
    const end = new Date(now);
    const nextBoundary = Math.ceil(now.getHours() / 6) * 6;
    end.setHours(nextBoundary, 0, 0, 0);
    if (end <= now) end.setHours(end.getHours() + 6);

    const tick = () => {
      const diff = Math.max(0, end.getTime() - Date.now());
      setTimeLeft({
        h: Math.floor(diff / 3600000),
        m: Math.floor((diff % 3600000) / 60000),
        s: Math.floor((diff % 60000) / 1000),
      });
    };
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, []);

  const FLASH_DISCOUNTS = [20, 30, 25, 35, 40, 45, 50, 28];
  const flashProducts = products.slice(0, 8).map((p, i) => ({
    ...p,
    flashDiscount: FLASH_DISCOUNTS[i % FLASH_DISCOUNTS.length],
  }));

  if (flashProducts.length === 0) return null;

  return (
    <section className="bg-white overflow-hidden">
      {/* 섹션 헤더 */}
      <div className="bg-red-600 px-5 py-4 flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-2.5">
          {/* SVG 번개 아이콘 */}
          <svg className="w-5 h-5 text-white flex-shrink-0" fill="currentColor" viewBox="0 0 24 24">
            <path d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <div>
            <h2 className="text-base font-bold text-white leading-none">번개특가</h2>
            <p className="text-white/70 text-xs mt-0.5">오늘만 특별 할인</p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <span className="text-white/60 text-xs mr-1.5">종료까지</span>
          {[timeLeft.h, timeLeft.m, timeLeft.s].map((v, i) => (
            <span key={i} className="flex items-center">
              <span className="bg-black/25 text-white font-bold text-sm w-8 h-8 flex items-center justify-center rounded tabular-nums">
                {String(v).padStart(2, '0')}
              </span>
              {i < 2 && <span className="text-white/60 font-bold text-sm mx-0.5">:</span>}
            </span>
          ))}
        </div>
      </div>
      {/* 상품 리스트 */}
      <div className="p-4 overflow-x-auto">
        <div className="flex gap-3 pb-1">
          {flashProducts.map((product) => {
            const originalPrice = Math.round(product.price / (1 - product.flashDiscount / 100) / 100) * 100;
            return (
              <a key={product.id} href={`/products/${product.id}`}
                className="flex-none w-32 group cursor-pointer">
                <div className="aspect-square overflow-hidden bg-gray-100 mb-2 relative">
                  {product.imageUrl ? (
                    <img src={product.imageUrl} alt={product.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" loading="lazy"/>
                  ) : (
                    <div className="w-full h-full flex items-center justify-center bg-gray-200">
                      <span className="text-xl font-bold text-gray-400">{product.name?.[0] || '상'}</span>
                    </div>
                  )}
                  <span className="absolute top-1.5 left-1.5 bg-red-600 text-white text-xs font-bold px-1.5 py-0.5 rounded-sm">
                    -{product.flashDiscount}%
                  </span>
                </div>
                <p className="text-xs text-gray-700 line-clamp-2 font-medium leading-snug mb-1 group-hover:text-red-600 transition-colors">{product.name}</p>
                <p className="text-xs text-gray-400 line-through">{originalPrice.toLocaleString()}원</p>
                <p className="text-sm font-bold text-red-600">{product.price.toLocaleString()}원</p>
              </a>
            );
          })}
        </div>
      </div>
    </section>
  );
}

// ── 최근 본 상품 ──────────────────────────────────────
function RecentlyViewedSection() {
  const [recentProducts, setRecentProducts] = useState<Product[]>([]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem('recentlyViewed');
      if (stored) setRecentProducts(JSON.parse(stored));
    } catch {}
  }, []);

  if (recentProducts.length === 0) return null;

  return (
    <section className="bg-white px-5 py-5">
      <SectionHeader title="최근 본 상품" />
      <div className="flex gap-3 overflow-x-auto pb-1 -mx-1 px-1">
        {recentProducts.slice(0, 12).map((product: Product) => (
          <a key={product.id} href={`/products/${product.id}`}
            className="flex-none w-24 group">
            <div className="aspect-square overflow-hidden bg-gray-100 mb-1.5">
              {product.imageUrl ? (
                <img src={product.imageUrl} alt={product.name}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" loading="lazy"/>
              ) : (
                <div className="w-full h-full flex items-center justify-center bg-gray-200">
                  <span className="text-lg font-bold text-gray-400">{product.name?.[0]}</span>
                </div>
              )}
            </div>
            <p className="text-xs text-gray-700 line-clamp-2 leading-snug mb-0.5 group-hover:text-red-600 transition-colors">{product.name}</p>
            <p className="text-xs font-bold text-gray-900">{product.price?.toLocaleString()}원</p>
          </a>
        ))}
      </div>
    </section>
  );
}

// ── 히어로 배너 ───────────────────────────────────────
function HeroBanner() {
  const [current, setCurrent] = useState(0);
  const router = useRouter();

  useEffect(() => {
    const timer = setInterval(() => setCurrent(c => (c + 1) % BANNERS.length), 4500);
    return () => clearInterval(timer);
  }, []);

  const banner = BANNERS[current];

  return (
    <div
      className="relative overflow-hidden h-56 md:h-72 transition-colors duration-700"
      style={{ backgroundColor: banner.bg }}
    >
      {/* 기하학적 장식 요소 */}
      <div
        className="absolute -right-16 -top-16 w-72 h-72 rounded-full opacity-20 transition-colors duration-700"
        style={{ backgroundColor: banner.accent }}
      />
      <div
        className="absolute right-12 bottom-0 w-44 h-44 rounded-full opacity-10 translate-y-1/3 transition-colors duration-700"
        style={{ backgroundColor: banner.accent }}
      />
      <div
        className="absolute right-6 top-1/2 -translate-y-1/2 w-20 h-20 rounded-full opacity-15 transition-colors duration-700"
        style={{ backgroundColor: banner.accent }}
      />

      {/* 컨텐츠 */}
      <div className="relative z-10 h-full flex items-center">
        <div className="px-8 md:px-14">
          <div
            className="text-[11px] font-bold uppercase tracking-[0.14em] mb-3 transition-colors duration-500"
            style={{ color: banner.accent }}
          >
            {banner.tag}
          </div>
          <h2 className="text-2xl md:text-4xl font-bold text-white leading-[1.2] mb-5 whitespace-pre-line">
            {banner.title}
          </h2>
          <button
            onClick={() => router.push(banner.href)}
            className="bg-white text-gray-900 font-semibold px-5 py-2.5 text-sm rounded hover:bg-gray-100 transition-colors"
          >
            {banner.cta} →
          </button>
        </div>
      </div>

      {/* 인디케이터 */}
      <div className="absolute bottom-4 left-8 flex items-center gap-1.5">
        {BANNERS.map((_, i) => (
          <button
            key={i}
            onClick={() => setCurrent(i)}
            className={`transition-all rounded-full ${i === current ? 'w-5 h-1.5 bg-white' : 'w-1.5 h-1.5 bg-white/35 hover:bg-white/60'}`}
          />
        ))}
      </div>

      {/* 화살표 */}
      <button
        onClick={() => setCurrent(c => (c - 1 + BANNERS.length) % BANNERS.length)}
        className="absolute left-3 top-1/2 -translate-y-1/2 w-8 h-8 bg-white/10 hover:bg-white/25 rounded-full flex items-center justify-center text-white transition-colors"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
      </button>
      <button
        onClick={() => setCurrent(c => (c + 1) % BANNERS.length)}
        className="absolute right-3 top-1/2 -translate-y-1/2 w-8 h-8 bg-white/10 hover:bg-white/25 rounded-full flex items-center justify-center text-white transition-colors"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
      </button>
    </div>
  );
}

// ── 섹션 헤더 (이모지 없는 클린 타이포그래피) ──────────
function SectionHeader({ title, subtitle, href }: { title: string; subtitle?: string; href?: string }) {
  return (
    <div className="flex items-end justify-between mb-4">
      <div>
        <h2 className="text-[17px] font-bold text-gray-900 leading-snug">{title}</h2>
        {subtitle && <p className="text-sm text-gray-500 mt-0.5">{subtitle}</p>}
      </div>
      {href && (
        <a href={href} className="text-sm text-gray-400 hover:text-gray-700 transition-colors flex items-center gap-0.5 pb-0.5">
          전체보기
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </a>
      )}
    </div>
  );
}

// ── 상품 스켈레톤 ─────────────────────────────────────
function ProductSkeleton() {
  return (
    <div className="bg-white border border-gray-100 overflow-hidden animate-pulse">
      <div className="aspect-square bg-gray-200" />
      <div className="p-3 space-y-2">
        <div className="h-3 bg-gray-200 rounded w-1/4" />
        <div className="h-4 bg-gray-200 rounded" />
        <div className="h-4 bg-gray-200 rounded w-3/4" />
        <div className="h-3 bg-gray-200 rounded w-1/2 mt-3" />
        <div className="h-6 bg-gray-200 rounded w-1/3" />
        <div className="h-9 bg-gray-200 rounded mt-2" />
      </div>
    </div>
  );
}

// ── 신규 회원 쿠폰 배너 ───────────────────────────────
const WELCOME_CODE = 'WELCOME3000';

function WelcomeCouponBanner() {
  const router = useRouter();
  const [claimed, setClaimed] = useState(false);

  useEffect(() => {
    const userId = localStorage.getItem('userId');
    if (userId && localStorage.getItem(`welcome_coupon_${userId}`)) {
      setClaimed(true);
    }
  }, []);

  const handleClick = () => {
    const userId = localStorage.getItem('userId');
    if (!userId) {
      router.push('/auth');
      return;
    }
    const key = `welcome_coupon_${userId}`;
    if (localStorage.getItem(key)) {
      toast.error('이미 수령한 쿠폰입니다', { icon: '🎫' });
      return;
    }
    localStorage.setItem(key, '1');
    setClaimed(true);
    navigator.clipboard.writeText(WELCOME_CODE).catch(() => {});
    toast.success(
      `🎉 쿠폰 코드 복사 완료!\n코드: ${WELCOME_CODE}\n주문 시 쿠폰 입력란에 붙여넣기 하세요`,
      { duration: 6000 }
    );
  };

  return (
    <button
      onClick={handleClick}
      className="group w-full text-left bg-gray-950 px-6 py-5 text-white hover:bg-gray-800 transition-colors flex items-center justify-between"
    >
      <div>
        <div className="text-[10px] font-semibold text-gray-500 uppercase tracking-[0.12em] mb-2">Welcome Offer</div>
        <div className="font-bold text-base">신규 회원 혜택</div>
        <div className="text-sm text-gray-400 mt-1">
          {claimed ? '✅ 쿠폰 수령 완료 · 코드: WELCOME3000' : '가입 즉시 3,000원 쿠폰 지급'}
        </div>
      </div>
      <div className="w-12 h-12 rounded bg-white/10 flex items-center justify-center flex-shrink-0 group-hover:bg-white/20 transition-colors">
        <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V5.5A2.5 2.5 0 109.5 8H12zm-7 4h14M5 12a2 2 0 110-4h14a2 2 0 110 4M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
        </svg>
      </div>
    </button>
  );
}

// ── 메인 홈 페이지 ────────────────────────────────────
export default function Home() {
  const { data: allProducts, isLoading } = useQuery({
    queryKey: ['products', 'home'],
    queryFn: () => productApi.getProducts({ page: 0, size: 20 }),
    staleTime: 60000,
  });

  const products = allProducts?.content || [];
  const electronics = products.filter((p: Product) => p.categoryId === 1).slice(0, 4);
  const fashion = products.filter((p: Product) => p.categoryId === 2).slice(0, 4);
  const featured = products.slice(0, 8);
  const newArrivals = [...products].reverse().slice(0, 8);

  return (
    <main className="min-h-screen bg-[#F4F4F4] pb-14 md:pb-0">
      <GlobalNav />

      <div className="max-w-[1280px] mx-auto px-4 py-4 space-y-3">

        {/* ── 프로모션 알림 바 ── */}
        <div className="bg-gray-950 rounded text-white">
          <div className="flex items-center justify-between px-4 py-2.5">
            <p className="text-sm text-gray-300">
              신규가입 즉시 <span className="text-white font-semibold">3,000원 쿠폰</span> 지급 + 카드결제 <span className="text-white font-semibold">5% 추가할인</span>
            </p>
            <a href="/auth" className="flex-shrink-0 ml-4 text-red-400 hover:text-red-300 font-semibold text-xs transition-colors flex items-center gap-0.5 whitespace-nowrap">
              지금 가입하기
              <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
            </a>
          </div>
        </div>

        {/* ── 히어로 배너 ── */}
        <HeroBanner />

        {/* ── 플래시세일 ── */}
        <FlashSaleSection products={products} />

        {/* ── 배송/혜택 바 (SVG 아이콘) ── */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
          {[
            {
              icon: (
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0" />
                </svg>
              ),
              title: '로켓배송',
              desc: '오늘 주문 · 내일 도착',
            },
            {
              icon: (
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              ),
              title: '5만원 이상 무료배송',
              desc: '전 품목 배송비 혜택',
            },
            {
              icon: (
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
              ),
              title: '안전결제',
              desc: '카드 · 계좌이체 · 간편결제',
            },
          ].map((item) => (
            <div key={item.title} className="bg-white border border-gray-100 px-4 py-3.5 flex items-center gap-3">
              <div className="text-gray-500 flex-shrink-0">{item.icon}</div>
              <div>
                <div className="font-semibold text-gray-900 text-sm">{item.title}</div>
                <div className="text-xs text-gray-500 mt-0.5">{item.desc}</div>
              </div>
            </div>
          ))}
        </div>

        {/* ── 카테고리 ── */}
        <section className="bg-white px-5 py-5">
          <SectionHeader title="카테고리" subtitle="원하는 상품을 빠르게 찾아보세요" />
          <div className="grid grid-cols-6 gap-2">
            {CATEGORIES.map((cat) => (
              <a
                key={cat.label}
                href={cat.href}
                className="flex flex-col items-center gap-2 group"
              >
                <div className={`w-12 h-12 ${cat.bg} rounded-xl flex items-center justify-center group-hover:opacity-85 transition-opacity`}>
                  <span className="text-white font-bold text-base">{cat.mono}</span>
                </div>
                <span className="text-xs font-medium text-gray-600 group-hover:text-gray-900 transition-colors text-center leading-tight">{cat.label}</span>
              </a>
            ))}
          </div>
        </section>

        {/* ── 에디터 추천 (리스트형) ── */}
        <section className="bg-white overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center gap-2.5">
            <span className="text-xs font-bold bg-red-600 text-white px-2 py-0.5 rounded-sm">MD PICK</span>
            <h2 className="text-base font-bold text-gray-900">에디터 추천</h2>
            <span className="text-xs text-gray-400 hidden sm:block ml-1">전문 MD가 엄선한 이달의 상품</span>
          </div>
          <div className="divide-y divide-gray-50">
            {[
              {
                title: '2026 신형 스마트폰 특가',
                desc: '최신 플래그십 모델 역대급 할인',
                badge: '-25%',
                badgeStyle: 'text-red-600 bg-red-50',
                href: '/search?cat=1',
                dot: 'bg-slate-800',
              },
              {
                title: '봄 시즌 신상 패션',
                desc: '트렌디한 봄 패션 아이템 총집합',
                badge: 'NEW',
                badgeStyle: 'text-emerald-700 bg-emerald-50',
                href: '/search?cat=2',
                dot: 'bg-rose-500',
              },
              {
                title: '홈 인테리어 위크',
                desc: '집을 더 예쁘게, 최대 40% 세일',
                badge: '~40%',
                badgeStyle: 'text-amber-700 bg-amber-50',
                href: '/search?cat=4',
                dot: 'bg-amber-500',
              },
            ].map((item, idx) => (
              <a key={item.title} href={item.href}
                className="flex items-center gap-4 px-5 py-4 hover:bg-gray-50 transition-colors group">
                <div className={`w-9 h-9 ${item.dot} rounded flex items-center justify-center flex-shrink-0`}>
                  <span className="text-white font-bold text-sm">{idx + 1}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 mb-0.5">
                    <span className={`text-xs font-semibold px-1.5 py-0.5 rounded-sm ${item.badgeStyle}`}>{item.badge}</span>
                  </div>
                  <h3 className="font-semibold text-sm text-gray-900 group-hover:text-red-600 transition-colors line-clamp-1">{item.title}</h3>
                  <p className="text-xs text-gray-500 mt-0.5">{item.desc}</p>
                </div>
                <svg className="w-4 h-4 text-gray-300 group-hover:text-red-500 flex-shrink-0 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </a>
            ))}
          </div>
        </section>

        {/* ── 오늘의 추천 ── */}
        <section className="bg-white px-5 py-5">
          <SectionHeader title="오늘의 추천" subtitle="가장 인기 있는 상품을 만나보세요" href="/products" />
          {isLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {Array.from({ length: 8 }).map((_, i) => <ProductSkeleton key={i} />)}
            </div>
          ) : featured.length > 0 ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {featured.map((product: Product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          ) : (
            <div className="text-center py-12 text-gray-400">
              <div className="w-12 h-12 mx-auto mb-3 bg-gray-100 rounded-full flex items-center justify-center">
                <svg className="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              </div>
              <p className="text-sm">상품을 불러오는 중...</p>
            </div>
          )}
        </section>

        {/* ── 전자기기 & 패션 ── */}
        {(electronics.length > 0 || fashion.length > 0) && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            {electronics.length > 0 && (
              <section className="bg-white px-5 py-5">
                <SectionHeader title="전자기기" href="/search?cat=1" />
                <div className="grid grid-cols-2 gap-3">
                  {electronics.map((product: Product) => (
                    <ProductCard key={product.id} product={product} />
                  ))}
                </div>
              </section>
            )}
            {fashion.length > 0 && (
              <section className="bg-white px-5 py-5">
                <SectionHeader title="패션" href="/search?cat=2" />
                <div className="grid grid-cols-2 gap-3">
                  {fashion.map((product: Product) => (
                    <ProductCard key={product.id} product={product} />
                  ))}
                </div>
              </section>
            )}
          </div>
        )}

        {/* ── 신상품 ── */}
        {newArrivals.length > 0 && (
          <section className="bg-white px-5 py-5">
            <SectionHeader title="신상품" subtitle="방금 입고된 새로운 상품들" href="/products" />
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {newArrivals.map((product: Product) => (
                <ProductCard key={`new-${product.id}`} product={product} />
              ))}
            </div>
          </section>
        )}

        {/* ── 최근 본 상품 ── */}
        <RecentlyViewedSection />

        {/* ── 하단 배너 ── */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <WelcomeCouponBanner />
          <a href="/seller" className="group bg-red-600 px-6 py-5 text-white hover:bg-red-700 transition-colors flex items-center justify-between">
            <div>
              <div className="text-[10px] font-semibold text-red-300 uppercase tracking-[0.12em] mb-2">Seller Program</div>
              <div className="font-bold text-base">판매자 시작하기</div>
              <div className="text-sm text-red-200 mt-1">지금 바로 판매를 시작해보세요</div>
            </div>
            <div className="w-12 h-12 rounded bg-white/15 flex items-center justify-center flex-shrink-0 group-hover:bg-white/25 transition-colors">
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
          </a>
        </div>
      </div>

      <CartSummary />

      {/* ── 푸터 ── */}
      <footer className="bg-gray-950 text-white mt-8">
        <div className="max-w-[1280px] mx-auto px-4 py-10">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-8">
            <div>
              <div className="text-[18px] font-black mb-3">
                <span className="text-red-500">Live</span>Mart
              </div>
              <p className="text-gray-500 text-sm leading-relaxed">
                빠른 배송과 최저가로<br />만족스러운 쇼핑 경험을<br />제공합니다.
              </p>
            </div>
            <div>
              <h4 className="font-semibold mb-3 text-gray-300 text-sm">쇼핑</h4>
              <ul className="space-y-2 text-sm text-gray-500">
                <li><a href="/products" className="hover:text-gray-300 transition-colors">전체 상품</a></li>
                <li><a href="/search" className="hover:text-gray-300 transition-colors">검색</a></li>
                <li><a href="/cart" className="hover:text-gray-300 transition-colors">장바구니</a></li>
                <li><a href="/wishlist" className="hover:text-gray-300 transition-colors">위시리스트</a></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold mb-3 text-gray-300 text-sm">고객센터</h4>
              <ul className="space-y-2 text-sm text-gray-500">
                <li><a href="/my-orders" className="hover:text-gray-300 transition-colors">주문 내역</a></li>
                <li><a href="/returns" className="hover:text-gray-300 transition-colors">반품/환불</a></li>
                <li><a href="/notifications" className="hover:text-gray-300 transition-colors">알림</a></li>
                <li><a href="/profile" className="hover:text-gray-300 transition-colors">내 정보</a></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold mb-3 text-gray-300 text-sm">비즈니스</h4>
              <ul className="space-y-2 text-sm text-gray-500">
                <li><a href="/seller" className="hover:text-gray-300 transition-colors">판매자센터</a></li>
                <li><a href="/admin" className="hover:text-gray-300 transition-colors">관리자</a></li>
                <li><a href="/seller/products" className="hover:text-gray-300 transition-colors">상품 등록</a></li>
                <li><a href="/admin/coupons" className="hover:text-gray-300 transition-colors">쿠폰 관리</a></li>
              </ul>
            </div>
          </div>
          <div className="border-t border-gray-800 pt-6 flex flex-col sm:flex-row items-center justify-between gap-3">
            <p className="text-gray-600 text-xs">© 2026 LiveMart. All rights reserved.</p>
            <div className="flex items-center gap-4 text-xs text-gray-600">
              <a href="#" className="hover:text-gray-400 transition-colors">이용약관</a>
              <a href="#" className="hover:text-gray-400 transition-colors">개인정보처리방침</a>
              <a href="#" className="hover:text-gray-400 transition-colors">고객센터</a>
            </div>
          </div>
        </div>
      </footer>
    </main>
  );
}
