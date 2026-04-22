'use client';

import React, { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useCartStore } from '@/store/cartStore';
import toast from 'react-hot-toast';

interface ProductCardProps {
  product: {
    id: number;
    name: string;
    description?: string;
    price: number;
    stockQuantity?: number;
    imageUrl?: string;
    category?: string;
    categoryId?: number;
    sellerId?: number;
  };
}

function getDiscount(id: number): number {
  const r = [0, 0, 5, 10, 10, 15, 15, 20, 20, 25, 30, 0, 10, 15];
  return r[id % r.length];
}
function getOriginal(price: number, d: number): number {
  return d === 0 ? price : Math.round(price / (1 - d / 100) / 100) * 100;
}

/* ── Skeleton ── */
export function ProductCardSkeleton() {
  return (
    <div className="overflow-hidden" style={{ background: 'var(--bg-card)', border: '1px solid var(--border-2)' }}>
      <div
        className="aspect-square"
        style={{
          background: 'linear-gradient(90deg, #EDEBE4 0%, #E4E1D8 50%, #EDEBE4 100%)',
          backgroundSize: '600px 100%',
          animation: 'shimmer 1.8s ease-in-out infinite',
        }}
      />
      <div className="p-3.5 space-y-2.5">
        <div className="h-2.5 rounded-sm animate-pulse" style={{ background: '#EDEBE4', width: '40%' }} />
        <div className="h-3 rounded-sm animate-pulse" style={{ background: '#EDEBE4', width: '90%' }} />
        <div className="h-3 rounded-sm animate-pulse" style={{ background: '#EDEBE4', width: '70%' }} />
        <div className="h-5 rounded-sm animate-pulse mt-1" style={{ background: '#EDEBE4', width: '50%' }} />
        <div className="h-9 rounded-sm animate-pulse mt-2" style={{ background: '#EDEBE4' }} />
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════
   PRODUCT CARD — Premium editorial
   ══════════════════════════════════════════════ */
export function ProductCard({ product }: ProductCardProps) {
  const router = useRouter();
  const addToCart = useCartStore(s => s.addItem);
  const [imgLoaded, setImgLoaded] = useState(false);
  const [imgErr, setImgErr] = useState(false);
  const [wishlisted, setWishlisted] = useState(false);
  const [wishAnim, setWishAnim] = useState(false);
  const [cartAdded, setCartAdded] = useState(false);
  const [hovered, setHovered] = useState(false);

  const discount = getDiscount(product.id);
  const original = getOriginal(product.price, discount);
  const outOfStock = (product.stockQuantity ?? 1) === 0;
  const lowStock = !outOfStock && (product.stockQuantity ?? 99) < 10;
  const freeShip = product.price >= 50000;

  const handleCart = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    if (outOfStock) return;
    addToCart({ productId: product.id, name: product.name, price: product.price, quantity: 1, imageUrl: product.imageUrl });
    setCartAdded(true);
    setTimeout(() => setCartAdded(false), 1400);
    toast.success('장바구니에 추가됐습니다', { duration: 1500 });
  }, [outOfStock, product, addToCart]);

  const handleWish = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setWishAnim(true);
    setTimeout(() => setWishAnim(false), 450);
    setWishlisted(w => !w);
    toast.success(wishlisted ? '위시리스트에서 제거됐습니다' : '위시리스트에 추가됐습니다', { duration: 1400 });
  }, [wishlisted]);

  return (
    <div
      className="cursor-pointer group flex flex-col overflow-hidden"
      style={{
        background: 'var(--bg-card)',
        border: `1px solid ${hovered ? 'var(--border)' : 'var(--border-2)'}`,
        transform: hovered ? 'translateY(-3px)' : 'translateY(0)',
        boxShadow: hovered ? '0 14px 40px rgba(14,12,10,0.12), 0 2px 8px rgba(14,12,10,0.06)' : '0 1px 3px rgba(14,12,10,0.04)',
        transition: 'transform 0.28s cubic-bezier(0.22,1,0.36,1), box-shadow 0.28s cubic-bezier(0.22,1,0.36,1), border-color 0.2s',
        willChange: 'transform',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={() => router.push(`/products/${product.id}`)}
    >
      {/* ── IMAGE ── */}
      <div className="relative aspect-square overflow-hidden" style={{ background: 'var(--bg-warm)' }}>
        {/* Skeleton */}
        {!imgLoaded && !imgErr && product.imageUrl && (
          <div
            className="absolute inset-0"
            style={{
              background: 'linear-gradient(90deg, #F0EEE7 0%, #E8E5DB 45%, #F0EEE7 100%)',
              backgroundSize: '600px 100%',
              animation: 'shimmer 1.8s ease-in-out infinite',
            }}
          />
        )}

        {product.imageUrl && !imgErr ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            onLoad={() => setImgLoaded(true)}
            onError={() => { setImgErr(true); setImgLoaded(true); }}
            className="w-full h-full object-cover"
            style={{
              transform: hovered ? 'scale(1.07)' : 'scale(1)',
              opacity: imgLoaded ? 1 : 0,
              transition: 'transform 0.55s cubic-bezier(0.22,1,0.36,1), opacity 0.3s ease',
            }}
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center" style={{ background: '#F0EEE7' }}>
            <svg className="w-9 h-9" style={{ color: '#C8C4BC' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1}
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </div>
        )}

        {/* Quick view overlay */}
        {!outOfStock && (
          <div
            className="absolute bottom-0 left-0 right-0 hidden lg:flex items-center justify-center gap-2 py-2.5"
            style={{
              background: 'rgba(10,10,10,0.85)',
              backdropFilter: 'blur(6px)',
              opacity: hovered ? 1 : 0,
              transform: hovered ? 'translateY(0)' : 'translateY(6px)',
              transition: 'opacity 0.25s ease, transform 0.25s cubic-bezier(0.22,1,0.36,1)',
            }}
            onClick={e => { e.stopPropagation(); router.push(`/products/${product.id}`); }}
          >
            <svg className="w-3.5 h-3.5 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0zM2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
            </svg>
            <span className="text-white text-[10px] font-black tracking-[0.18em] uppercase">빠른 보기</span>
          </div>
        )}

        {/* Top-left badges */}
        <div className="absolute top-2 left-2 flex flex-col gap-1">
          {discount > 0 && (
            <span
              className="font-bebas text-white px-2 py-0.5 leading-tight"
              style={{
                background: '#E8001D',
                fontSize: '0.85rem',
                letterSpacing: '0.04em',
                animation: 'badgePop 0.35s cubic-bezier(0.175,0.885,0.32,1.275) both',
              }}
            >
              -{discount}%
            </span>
          )}
          {lowStock && (
            <span
              className="text-white text-[10px] font-black px-2 py-0.5"
              style={{
                background: '#0A0A0A',
                animation: 'badgePop 0.35s cubic-bezier(0.175,0.885,0.32,1.275) both 0.05s',
              }}
            >
              {product.stockQuantity}개 남음
            </span>
          )}
        </div>

        {/* Out of stock overlay */}
        {outOfStock && (
          <div
            className="absolute inset-0 flex items-center justify-center"
            style={{ background: 'rgba(247,246,241,0.7)', backdropFilter: 'blur(3px)' }}
          >
            <span
              className="font-bebas text-white px-5 py-1.5 tracking-[0.2em]"
              style={{ background: '#0A0A0A', fontSize: '1rem' }}
            >
              품절
            </span>
          </div>
        )}

        {/* Wishlist button */}
        <button
          className="absolute top-2 right-2 w-8 h-8 flex items-center justify-center transition-all"
          style={{
            background: 'var(--bg-card)',
            boxShadow: '0 2px 8px rgba(0,0,0,0.10)',
            opacity: hovered || wishlisted ? 1 : 0,
            transform: wishAnim ? 'scale(1.4)' : 'scale(1)',
            transition: 'opacity 0.2s ease, transform 0.35s cubic-bezier(0.175,0.885,0.32,1.275)',
          }}
          onClick={handleWish}
          aria-label={wishlisted ? '위시리스트에서 제거' : '위시리스트에 추가'}
        >
          {wishAnim && (
            <span
              className="absolute inset-0"
              style={{
                background: '#FFF0F0',
                animation: 'pingOnce 0.6s cubic-bezier(0,0,0.2,1) forwards',
              }}
            />
          )}
          <svg
            className="w-4 h-4 relative z-10 transition-colors duration-200"
            fill={wishlisted ? '#E8001D' : 'none'}
            stroke={wishlisted ? '#E8001D' : '#AEAEAE'}
            strokeWidth={wishlisted ? 0 : 1.8}
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round"
              d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
          </svg>
        </button>
      </div>

      {/* ── PRODUCT INFO ── */}
      <div className="p-3.5 flex flex-col flex-1">
        {product.category && (
          <span className="text-[10px] uppercase tracking-[0.14em] mb-1.5 font-bold" style={{ color: 'var(--text-4)' }}>
            {product.category}
          </span>
        )}

        <h3
          className="line-clamp-2 mb-2 flex-1 leading-snug"
          style={{ fontSize: '12.5px', fontWeight: 500, color: 'var(--text-2)', wordBreak: 'keep-all' }}
        >
          {product.name}
        </h3>

        {/* Price block */}
        <div className="mb-3">
          {discount > 0 && (
            <div className="flex items-center gap-1.5 mb-0.5">
              <span className="text-[11px] font-black" style={{ color: '#E8001D' }}>{discount}%</span>
              <span className="text-[11px] line-through tabular-nums" style={{ color: '#C0BDB5' }}>
                {original.toLocaleString()}원
              </span>
            </div>
          )}
          <div className="flex items-baseline gap-0.5">
            <span
              className="font-bebas tabular-nums leading-none"
              style={{
                fontSize: 'clamp(1.2rem, 2.5vw, 1.5rem)',
                color: 'var(--text)',
                letterSpacing: '0.01em',
              }}
            >
              {product.price.toLocaleString()}
            </span>
            <span className="text-xs mb-0.5" style={{ color: 'var(--text-3)' }}>원</span>
          </div>
        </div>

        {/* Meta */}
        <div className="flex items-center gap-2 mb-3 min-h-[16px]">
          {freeShip ? (
            <span className="text-[11px] font-bold" style={{ color: '#0070F3' }}>무료배송</span>
          ) : (
            <span className="text-[11px]" style={{ color: '#AEAEAE' }}>배송비 3,000원</span>
          )}
        </div>

        {/* Cart button */}
        <button
          onClick={handleCart}
          disabled={outOfStock}
          className="w-full py-2.5 flex items-center justify-center gap-1.5 font-black text-[11px] tracking-[0.08em] uppercase transition-all duration-250"
          style={{
            background: outOfStock ? 'var(--bg-warm)' : cartAdded ? '#00A854' : 'var(--bg-dark)',
            color: outOfStock ? '#C0BDB5' : '#FFFFFF',
            cursor: outOfStock ? 'not-allowed' : 'pointer',
            transition: 'background 0.3s ease, transform 0.15s ease',
          }}
          onMouseEnter={e => !outOfStock && !cartAdded && (e.currentTarget.style.background = '#E8001D')}
          onMouseLeave={e => !outOfStock && !cartAdded && (e.currentTarget.style.background = '#0A0A0A')}
        >
          {outOfStock ? (
            '품절'
          ) : cartAdded ? (
            <>
              <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7"/>
              </svg>
              담김
            </>
          ) : (
            <>
              <svg className="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
              </svg>
              장바구니
            </>
          )}
        </button>
      </div>
    </div>
  );
}
