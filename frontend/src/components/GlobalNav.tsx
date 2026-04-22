'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useCartStore } from '@/store/cartStore';
import { searchApi } from '@/api/productApi';

// ── 카테고리 + 메가 메뉴 데이터 ─────────────────────────────
const CATEGORIES = [
  {
    label: '전자기기', href: '/products?cat=1',
    sub: [
      { label: '스마트폰', href: '/products?cat=1&sub=phone' },
      { label: '노트북', href: '/products?cat=1&sub=laptop' },
      { label: '태블릿', href: '/products?cat=1&sub=tablet' },
      { label: 'TV/모니터', href: '/products?cat=1&sub=display' },
      { label: '오디오', href: '/products?cat=1&sub=audio' },
      { label: '카메라', href: '/products?cat=1&sub=camera' },
    ],
    featured: { label: '이번주 특가', badge: '-30%', color: '#E8001D' },
  },
  {
    label: '패션', href: '/products?cat=2',
    sub: [
      { label: '남성의류', href: '/products?cat=2&sub=men' },
      { label: '여성의류', href: '/products?cat=2&sub=women' },
      { label: '신발', href: '/products?cat=2&sub=shoes' },
      { label: '가방', href: '/products?cat=2&sub=bag' },
      { label: '액세서리', href: '/products?cat=2&sub=acc' },
      { label: '스포츠웨어', href: '/products?cat=2&sub=sports' },
    ],
    featured: { label: '봄 신상', badge: 'NEW', color: '#059669' },
  },
  {
    label: '식품', href: '/products?cat=3',
    sub: [
      { label: '신선식품', href: '/products?cat=3&sub=fresh' },
      { label: '간편식', href: '/products?cat=3&sub=ready' },
      { label: '건강식품', href: '/products?cat=3&sub=health' },
      { label: '음료', href: '/products?cat=3&sub=drink' },
      { label: '과자/스낵', href: '/products?cat=3&sub=snack' },
      { label: '유제품', href: '/products?cat=3&sub=dairy' },
    ],
    featured: { label: '오늘의 신선', badge: 'FRESH', color: '#0891B2' },
  },
  {
    label: '홈/리빙', href: '/products?cat=4',
    sub: [
      { label: '가구', href: '/products?cat=4&sub=furniture' },
      { label: '침구/커튼', href: '/products?cat=4&sub=bedding' },
      { label: '조명', href: '/products?cat=4&sub=light' },
      { label: '주방용품', href: '/products?cat=4&sub=kitchen' },
      { label: '욕실용품', href: '/products?cat=4&sub=bath' },
      { label: '수납/정리', href: '/products?cat=4&sub=storage' },
    ],
    featured: { label: '홈인테리어', badge: '~40%', color: '#B45309' },
  },
  {
    label: '뷰티', href: '/products?cat=5',
    sub: [
      { label: '스킨케어', href: '/products?cat=5&sub=skin' },
      { label: '메이크업', href: '/products?cat=5&sub=makeup' },
      { label: '헤어케어', href: '/products?cat=5&sub=hair' },
      { label: '향수', href: '/products?cat=5&sub=perfume' },
      { label: '선케어', href: '/products?cat=5&sub=sun' },
      { label: '남성뷰티', href: '/products?cat=5&sub=men' },
    ],
    featured: { label: '뷰티 위크', badge: 'HOT', color: '#DB2777' },
  },
  {
    label: '스포츠', href: '/products?cat=6',
    sub: [
      { label: '운동기구', href: '/products?cat=6&sub=equip' },
      { label: '아웃도어', href: '/products?cat=6&sub=outdoor' },
      { label: '구기종목', href: '/products?cat=6&sub=ball' },
      { label: '수영/수상', href: '/products?cat=6&sub=swim' },
      { label: '사이클링', href: '/products?cat=6&sub=bike' },
      { label: '스포츠영양', href: '/products?cat=6&sub=nutrition' },
    ],
    featured: { label: '여름 준비', badge: 'EVENT', color: '#EA580C' },
  },
  { label: '전체상품', href: '/products', sub: [], featured: null },
];

// ── 모바일 하단 네비 ──────────────────────────────────────────
function MobileBottomNav() {
  const pathname = usePathname();
  const cartItems = useCartStore((state) => state.items);
  const totalQty = cartItems.reduce((s, i) => s + i.quantity, 0);

  const items = [
    {
      href: '/', label: '홈',
      icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/>,
      icon2: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 22V12h6v10"/>,
    },
    { href: '/products', label: '카테고리', icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 6h16M4 10h16M4 14h16M4 18h16"/> },
    { href: '/search', label: '검색', icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/> },
    { href: '/cart', label: '장바구니', badge: totalQty, icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/> },
    { href: '/profile', label: '마이', icon: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/> },
  ];

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-40 md:hidden"
      style={{
        background: 'rgba(247,246,241,0.97)',
        backdropFilter: 'blur(20px) saturate(1.8)',
        borderTop: '1px solid rgba(14,14,14,0.08)',
      }}
    >
      <div className="grid grid-cols-5 h-14">
        {items.map(({ href, label, icon, icon2, badge }) => {
          const active = pathname === href || (href !== '/' && pathname.startsWith(href));
          return (
            <a
              key={href}
              href={href}
              className="flex flex-col items-center justify-center gap-0.5 transition-colors"
              style={{ color: active ? '#E8001D' : 'rgba(14,14,14,0.35)' }}
            >
              <div className="relative">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  {icon}{icon2}
                </svg>
                {badge != null && badge > 0 && (
                  <span
                    className="absolute -top-1.5 -right-1.5 text-white text-[10px] font-black rounded-full min-w-[15px] h-[15px] flex items-center justify-center px-0.5"
                    style={{ background: '#E8001D' }}
                  >
                    {badge > 99 ? '99+' : badge}
                  </span>
                )}
              </div>
              <span className="text-[10px] font-semibold">{label}</span>
            </a>
          );
        })}
      </div>
    </nav>
  );
}

// ── 메가 메뉴 드롭다운 ────────────────────────────────────────
function MegaMenu({ category, onClose }: { category: typeof CATEGORIES[0]; onClose: () => void }) {
  if (!category.sub.length) return null;

  return (
    <div
      className="absolute top-full left-0 right-0 z-50"
      style={{
        background: '#F7F6F1',
        borderTop: '2px solid #E8001D',
        boxShadow: '0 20px 60px rgba(14,14,14,0.12)',
        animation: 'dropdownOpen 0.22s cubic-bezier(0.22,1,0.36,1) both',
      }}
      onMouseLeave={onClose}
    >
      <div className="max-w-[1440px] mx-auto px-4 lg:px-8 xl:px-12 py-6">
        <div className="flex gap-12">
          {/* 서브 카테고리 그리드 */}
          <div className="flex-1">
            <div
              className="text-[10px] font-black uppercase tracking-[0.2em] mb-4"
              style={{ color: 'rgba(14,14,14,0.3)' }}
            >
              {category.label} 카테고리
            </div>
            <div className="grid grid-cols-3 lg:grid-cols-6 gap-x-4 gap-y-1">
              {category.sub.map((item) => (
                <a
                  key={item.label}
                  href={item.href}
                  onClick={onClose}
                  className="group flex items-center gap-1.5 py-1.5 text-[13px] font-medium transition-colors"
                  style={{ color: 'rgba(14,14,14,0.65)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#E8001D'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.65)'; }}
                >
                  <span
                    className="w-0 group-hover:w-1.5 h-px transition-all duration-200 overflow-hidden flex-shrink-0"
                    style={{ background: '#E8001D', height: '1.5px' }}
                  />
                  {item.label}
                </a>
              ))}
            </div>
          </div>

          {/* 특가 배너 */}
          {category.featured && (
            <div className="hidden lg:flex flex-col justify-center flex-shrink-0 w-44">
              <a
                href={category.href}
                onClick={onClose}
                className="group p-4 transition-all"
                style={{ border: '1px solid rgba(14,14,14,0.1)', background: '#fff' }}
                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = '#E8001D'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(14,14,14,0.1)'; }}
              >
                <span
                  className="text-[10px] font-black px-1.5 py-0.5 mb-2 inline-block text-white"
                  style={{ background: category.featured.color }}
                >
                  {category.featured.badge}
                </span>
                <div className="text-sm font-bold text-[#0E0E0E] group-hover:text-[#E8001D] transition-colors">
                  {category.featured.label}
                </div>
                <div className="flex items-center gap-1 mt-1 text-xs" style={{ color: 'rgba(14,14,14,0.35)' }}>
                  보러가기
                  <svg className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7"/>
                  </svg>
                </div>
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ── 다크모드 토글 훅 ──────────────────────────────────────────
function useDarkMode() {
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem('livemart-theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const shouldBeDark = stored === 'dark' || (!stored && prefersDark);
    setIsDark(shouldBeDark);
    document.documentElement.classList.toggle('dark', shouldBeDark);
  }, []);

  const toggle = useCallback(() => {
    setIsDark(prev => {
      const next = !prev;
      document.documentElement.classList.toggle('dark', next);
      localStorage.setItem('livemart-theme', next ? 'dark' : 'light');
      return next;
    });
  }, []);

  return { isDark, toggle };
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────
export function GlobalNav() {
  const pathname = usePathname();
  const router = useRouter();
  const cartItems = useCartStore((state) => state.items);
  const [userName, setUserName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [activeMega, setActiveMega] = useState<string | null>(null);
  const megaTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const searchContainerRef = useRef<HTMLDivElement>(null);
  const { isDark, toggle: toggleDark } = useDarkMode();

  const totalQty = cartItems.reduce((sum, i) => sum + i.quantity, 0);

  useEffect(() => {
    setUserName(localStorage.getItem('userName'));
    setUserRole(localStorage.getItem('userRole'));
    const onScroll = () => setScrolled(window.scrollY > 4);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const data = await searchApi.autocomplete(searchQuery);
        const list: string[] = Array.isArray(data) ? data : (data?.suggestions || []);
        setSuggestions(list.slice(0, 8));
        setShowSuggestions(list.length > 0);
      } catch {
        const fallback = [searchQuery, `${searchQuery} 추천`, `${searchQuery} 최저가`, `${searchQuery} 신상품`];
        setSuggestions(fallback);
        setShowSuggestions(true);
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const handleSearch = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    setShowSuggestions(false);
    if (searchQuery.trim()) router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
  }, [searchQuery, router]);

  const handleLogout = useCallback(async () => {
    try { await fetch('/api/users/logout', { method: 'POST', credentials: 'include' }); } catch (_) {}
    ['userId', 'userName', 'userRole'].forEach(k => localStorage.removeItem(k));
    window.location.href = '/';
  }, []);

  const handleMegaEnter = useCallback((label: string) => {
    if (megaTimerRef.current) clearTimeout(megaTimerRef.current);
    setActiveMega(label);
  }, []);

  const handleMegaLeave = useCallback(() => {
    megaTimerRef.current = setTimeout(() => setActiveMega(null), 120);
  }, []);

  const handleMegaClose = useCallback(() => {
    if (megaTimerRef.current) clearTimeout(megaTimerRef.current);
    setActiveMega(null);
  }, []);

  const activeCategoryData = CATEGORIES.find(c => c.label === activeMega);

  // Warm cream with scroll glass effect
  const headerBg = scrolled
    ? 'rgba(247,246,241,0.96)'
    : '#F7F6F1';

  return (
    <>
      <header
        className="fixed top-0 left-0 right-0 z-50"
        style={{
          background: headerBg,
          backdropFilter: scrolled ? 'blur(24px) saturate(1.6)' : 'none',
          boxShadow: scrolled
            ? '0 1px 0 rgba(14,14,14,0.08), 0 4px 24px rgba(14,14,14,0.06)'
            : '0 1px 0 rgba(14,14,14,0.07)',
          transition: 'background 0.3s ease, box-shadow 0.3s ease',
        }}
      >
        {/* ── 상단 안내바 ── */}
        <div style={{ background: '#0A0A0A' }}>
          <div className="max-w-[1440px] mx-auto px-4">
            <div className="flex items-center h-8">
              {/* 마키 배송 알림 */}
              <div className="flex-1 overflow-hidden hidden sm:block">
                <div
                  className="flex gap-0 whitespace-nowrap"
                  style={{ animation: 'marquee 28s linear infinite' }}
                >
                  {[
                    '신규가입 쿠폰 — 지금 바로 할인 받으세요',
                    '  ·  ',
                    '5만원 이상 구매 시 전품목 무료배송',
                    '  ·  ',
                    'Stripe 안전결제 — 간편하고 빠르게',
                    '  ·  ',
                    '신상품 매일 업데이트',
                    '  ·  ',
                    '신규가입 쿠폰 — 지금 바로 할인 받으세요',
                    '  ·  ',
                    '5만원 이상 구매 시 전품목 무료배송',
                    '  ·  ',
                    'Stripe 안전결제 — 간편하고 빠르게',
                    '  ·  ',
                    '신상품 매일 업데이트',
                    '  ·  ',
                  ].map((text, i) => (
                    <span key={i} style={{ color: 'rgba(255,255,255,0.3)', fontSize: '11px' }}>{text}</span>
                  ))}
                </div>
              </div>
              <div className="sm:hidden" style={{ color: 'rgba(255,255,255,0.3)', fontSize: '11px' }}>
                LiveMart — 빠른배송 · 최저가
              </div>
              {/* 오른쪽 유틸 */}
              <div className="flex items-center gap-4 ml-auto flex-shrink-0" style={{ fontSize: '11px' }}>
                {userName ? (
                  <>
                    <span style={{ color: 'rgba(255,255,255,0.35)' }}>{userName}님</span>
                    <button
                      onClick={handleLogout}
                      style={{ color: 'rgba(255,255,255,0.35)' }}
                      className="hover:text-white transition-colors"
                    >
                      로그아웃
                    </button>
                  </>
                ) : (
                  <>
                    <a href="/auth" style={{ color: 'rgba(255,255,255,0.35)' }} className="hover:text-white transition-colors">로그인</a>
                    <a href="/auth" style={{ color: 'rgba(255,255,255,0.35)' }} className="hover:text-white transition-colors hidden sm:block">회원가입</a>
                  </>
                )}
                <a href="/seller" style={{ color: 'rgba(255,255,255,0.35)' }} className="hover:text-white transition-colors hidden md:block">판매자센터</a>
                {userRole === 'ADMIN' && (
                  <a href="/admin" style={{ color: '#FBBF24', fontWeight: 700 }} className="hover:opacity-80 transition-opacity hidden md:block">관리자</a>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* ── 메인 헤더 ── */}
        <div style={{ borderBottom: '1px solid rgba(14,14,14,0.07)' }}>
          <div className="max-w-[1440px] mx-auto px-4">
            <div className="flex items-center gap-5 h-[62px]">

              {/* 로고 — Bebas Neue display wordmark */}
              <a href="/" className="flex-shrink-0 group flex items-baseline gap-0" aria-label="LiveMart 홈">
                <span
                  className="font-bebas tracking-wider leading-none"
                  style={{
                    fontSize: '1.65rem',
                    color: '#E8001D',
                    letterSpacing: '0.04em',
                  }}
                >
                  LIVE
                </span>
                <span
                  className="font-bebas tracking-wider leading-none"
                  style={{
                    fontSize: '1.65rem',
                    color: '#0E0E0E',
                    letterSpacing: '0.04em',
                  }}
                >
                  MART
                </span>
              </a>

              {/* 검색창 */}
              <div ref={searchContainerRef} className="relative flex-1 max-w-2xl">
                <form onSubmit={(e) => { setShowSuggestions(false); handleSearch(e); }}>
                  <div
                    className="flex overflow-hidden transition-all duration-300"
                    style={{
                      border: '1.5px solid',
                      borderColor: scrolled ? 'rgba(232,0,29,0.25)' : 'rgba(14,14,14,0.14)',
                      background: '#fff',
                    }}
                  >
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
                      placeholder="상품, 브랜드를 검색해보세요"
                      className="flex-1 px-4 py-2.5 text-[13px] outline-none bg-transparent"
                      style={{ color: '#0E0E0E' }}
                      autoComplete="off"
                    />
                    <button
                      type="submit"
                      className="px-5 text-white flex items-center gap-1.5 font-bold text-[13px] transition-opacity hover:opacity-90"
                      style={{ background: '#E8001D' }}
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      <span className="hidden sm:inline">검색</span>
                    </button>
                  </div>
                </form>

                {/* 자동완성 */}
                {showSuggestions && suggestions.length > 0 && (
                  <div
                    className="absolute top-full left-0 right-0 mt-1 z-[60] overflow-hidden"
                    style={{
                      background: '#fff',
                      border: '1px solid rgba(14,14,14,0.09)',
                      boxShadow: '0 12px 40px rgba(14,14,14,0.1)',
                      animation: 'dropdownOpen 0.18s cubic-bezier(0.22,1,0.36,1) both',
                    }}
                  >
                    {suggestions.map((s, i) => (
                      <button
                        key={i}
                        type="button"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          setSearchQuery(s);
                          setShowSuggestions(false);
                          router.push(`/search?q=${encodeURIComponent(s)}`);
                        }}
                        className="w-full text-left px-4 py-2.5 flex items-center gap-3 transition-colors group"
                        style={{
                          fontSize: '13px',
                          color: 'rgba(14,14,14,0.7)',
                          borderBottom: i < suggestions.length - 1 ? '1px solid rgba(14,14,14,0.04)' : 'none',
                        }}
                        onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = '#FFF5F5'; (e.currentTarget as HTMLElement).style.color = '#E8001D'; }}
                        onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = ''; (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.7)'; }}
                      >
                        <svg className="w-3.5 h-3.5 flex-shrink-0 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                        </svg>
                        <span>{s}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* 우측 아이콘 그룹 */}
              <div className="flex items-center gap-0 flex-shrink-0">
                {/* 위시리스트 */}
                <a
                  href="/wishlist"
                  className="hidden sm:flex flex-col items-center gap-0.5 px-3 py-2 transition-colors group"
                  style={{ color: 'rgba(14,14,14,0.45)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.45)'; }}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                  </svg>
                  <span className="text-[11px] font-medium">위시리스트</span>
                </a>

                {/* 주문내역 */}
                <a
                  href="/my-orders"
                  className="hidden md:flex flex-col items-center gap-0.5 px-3 py-2 transition-colors"
                  style={{ color: 'rgba(14,14,14,0.45)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.45)'; }}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                  <span className="text-[11px] font-medium">주문내역</span>
                </a>

                {/* 내 정보/로그인 */}
                <a
                  href={userName ? '/profile' : '/auth'}
                  className="hidden md:flex flex-col items-center gap-0.5 px-3 py-2 transition-colors"
                  style={{ color: 'rgba(14,14,14,0.45)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.45)'; }}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                  <span className="text-[11px] font-medium">{userName ? '내정보' : '로그인'}</span>
                </a>

                {/* 장바구니 */}
                <a
                  href="/cart"
                  className="flex flex-col items-center gap-0.5 px-3 py-2 transition-colors group relative"
                  style={{ color: 'rgba(14,14,14,0.45)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.45)'; }}
                >
                  <div className="relative">
                    <svg className="w-5 h-5 transition-transform duration-200 group-hover:scale-110" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
                    </svg>
                    {totalQty > 0 && (
                      <span
                        className="absolute -top-1.5 -right-2 text-white text-[10px] font-black rounded-full min-w-[16px] h-[16px] flex items-center justify-center px-0.5 leading-none"
                        style={{
                          background: '#E8001D',
                          animation: 'badgePop 0.35s cubic-bezier(0.175,0.885,0.32,1.275) both',
                        }}
                      >
                        {totalQty > 99 ? '99+' : totalQty}
                      </span>
                    )}
                  </div>
                  <span className="text-[11px] font-medium">장바구니</span>
                </a>

                {/* 다크모드 토글 */}
                <button
                  onClick={toggleDark}
                  className="hidden sm:flex flex-col items-center gap-0.5 px-3 py-2 transition-colors"
                  style={{ color: 'rgba(14,14,14,0.45)' }}
                  aria-label={isDark ? '라이트 모드' : '다크 모드'}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.45)'; }}
                >
                  {isDark ? (
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                        d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364-6.364l-.707.707M6.343 17.657l-.707.707M17.657 17.657l-.707-.707M6.343 6.343l-.707-.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"/>
                    </svg>
                  ) : (
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8}
                        d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"/>
                    </svg>
                  )}
                  <span className="text-[11px] font-medium">{isDark ? '라이트' : '다크'}</span>
                </button>

                {/* 모바일 햄버거 */}
                <button
                  onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                  className="md:hidden p-2 ml-1 transition-colors"
                  style={{ color: 'rgba(14,14,14,0.55)' }}
                  aria-label="메뉴"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    {mobileMenuOpen
                      ? <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                      : <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                    }
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* ── 카테고리 네비 + 메가 메뉴 ── */}
        <div
          className="relative"
          style={{ borderBottom: '1px solid rgba(14,14,14,0.07)' }}
          onMouseLeave={handleMegaLeave}
        >
          <div className="max-w-[1440px] mx-auto px-4">
            <div className="flex items-center overflow-x-auto" style={{ scrollbarWidth: 'none' }}>
              {CATEGORIES.map((cat) => {
                const isActive = pathname === cat.href;
                const isMegaActive = activeMega === cat.label;
                return (
                  <div key={cat.label} className="relative flex-shrink-0">
                    <a
                      href={cat.href}
                      onMouseEnter={() => handleMegaEnter(cat.label)}
                      className="flex items-center gap-1 px-3 py-3 text-[13px] font-semibold whitespace-nowrap -mb-px transition-all duration-200"
                      style={{
                        color: isActive || isMegaActive ? '#E8001D' : 'rgba(14,14,14,0.6)',
                        borderBottom: `2px solid ${isActive || isMegaActive ? '#E8001D' : 'transparent'}`,
                      }}
                    >
                      {cat.label}
                      {cat.sub.length > 0 && (
                        <svg
                          className="w-3 h-3 transition-transform duration-200"
                          style={{ transform: isMegaActive ? 'rotate(180deg)' : 'none' }}
                          fill="none" stroke="currentColor" viewBox="0 0 24 24"
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                      )}
                    </a>
                  </div>
                );
              })}

              <div className="ml-auto pl-4 flex-shrink-0" style={{ borderLeft: '1px solid rgba(14,14,14,0.07)' }}>
                <a
                  href="/notifications"
                  className="flex items-center gap-1.5 px-3 py-3 text-[13px] font-medium whitespace-nowrap transition-colors"
                  style={{ color: 'rgba(14,14,14,0.4)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#E8001D'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.4)'; }}
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                  </svg>
                  알림
                </a>
              </div>
            </div>
          </div>

          {/* 메가 메뉴 드롭다운 */}
          {activeMega && activeCategoryData && activeCategoryData.sub.length > 0 && (
            <MegaMenu category={activeCategoryData} onClose={handleMegaClose} />
          )}
        </div>

        {/* ── 모바일 메뉴 ── */}
        {mobileMenuOpen && (
          <div
            className="md:hidden"
            style={{
              background: '#F7F6F1',
              borderTop: '1px solid rgba(14,14,14,0.07)',
              boxShadow: '0 12px 32px rgba(14,14,14,0.1)',
              animation: 'dropdownOpen 0.22s cubic-bezier(0.22,1,0.36,1) both',
            }}
          >
            <div className="max-w-[1440px] mx-auto px-4 py-3 space-y-0.5">
              {CATEGORIES.map((cat) => (
                <a
                  key={cat.label}
                  href={cat.href}
                  className="flex items-center justify-between px-3 py-2.5 text-[13px] font-medium transition-colors"
                  style={{ color: 'rgba(14,14,14,0.65)' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#E8001D'; (e.currentTarget as HTMLElement).style.background = 'rgba(232,0,29,0.04)'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.65)'; (e.currentTarget as HTMLElement).style.background = ''; }}
                >
                  {cat.label}
                  {cat.sub.length > 0 && (
                    <svg className="w-3.5 h-3.5 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7"/>
                    </svg>
                  )}
                </a>
              ))}
              <div className="pt-2 mt-2 space-y-0.5" style={{ borderTop: '1px solid rgba(14,14,14,0.07)' }}>
                {[
                  { href: '/my-orders', label: '주문내역' },
                  { href: '/wishlist', label: '위시리스트' },
                  { href: '/notifications', label: '알림' },
                  { href: userName ? '/profile' : '/auth', label: userName ? `${userName} (내 정보)` : '로그인 / 회원가입' },
                ].map(item => (
                  <a
                    key={item.href}
                    href={item.href}
                    className="flex items-center px-3 py-2.5 text-[13px] font-medium transition-colors"
                    style={{ color: 'rgba(14,14,14,0.5)' }}
                    onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = '#0E0E0E'; }}
                    onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(14,14,14,0.5)'; }}
                  >
                    {item.label}
                  </a>
                ))}
              </div>
            </div>
          </div>
        )}
      </header>

      {/* 헤더 높이 스페이서 */}
      <div className="h-[148px]" aria-hidden />
      <MobileBottomNav />
    </>
  );
}
