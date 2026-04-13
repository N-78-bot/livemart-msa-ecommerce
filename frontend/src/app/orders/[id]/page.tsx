'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { GlobalNav } from '@/components/GlobalNav';
import toast from 'react-hot-toast';

interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  productPrice: number;
  quantity: number;
  totalPrice: number;
}

interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  productPrice: number;
  quantity: number;
  totalPrice: number;
}

interface OrderDetail {
  id: number;
  orderNumber?: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  deliveryAddress?: string;
  phoneNumber?: string;
  orderNote?: string;
  paymentMethod?: string;
  items?: OrderItem[];
  trackingNumber?: string;
}

const STATUS_STEPS = [
  { key: 'PAYMENT_COMPLETED', label: '결제완료' },
  { key: 'PREPARING', label: '상품준비' },
  { key: 'SHIPPED', label: '배송중' },
  { key: 'DELIVERED', label: '배송완료' },
];

const STATUS_MAP: Record<string, string> = {
  PENDING: '주문 대기', CONFIRMED: '주문 확인', PAYMENT_PENDING: '결제 대기',
  PAYMENT_COMPLETED: '결제 완료', PREPARING: '상품 준비중', SHIPPED: '배송중',
  DELIVERED: '배송 완료', CANCELLED: '취소됨', RETURN_REQUESTED: '반품 신청',
};

type StatusStyleKey = keyof typeof STATUS_STYLE;

const STATUS_STYLE: Record<string, { bg: string; color: string; border: string }> = {
  DELIVERED:         { bg: 'rgba(34,197,94,0.08)',   color: 'rgb(21,128,61)',    border: 'rgba(34,197,94,0.3)' },
  CANCELLED:         { bg: 'rgba(14,14,14,0.06)',     color: 'rgba(14,14,14,0.45)', border: 'rgba(14,14,14,0.1)' },
  SHIPPED:           { bg: 'rgba(168,85,247,0.08)',   color: 'rgb(126,34,206)',   border: 'rgba(168,85,247,0.3)' },
  RETURN_REQUESTED:  { bg: 'rgba(249,115,22,0.08)',   color: 'rgb(194,65,12)',    border: 'rgba(249,115,22,0.3)' },
  PAYMENT_COMPLETED: { bg: 'rgba(59,130,246,0.08)',   color: 'rgb(37,99,235)',    border: 'rgba(59,130,246,0.3)' },
};
const DEFAULT_STATUS_STYLE = { bg: 'rgba(59,130,246,0.08)', color: 'rgb(37,99,235)', border: 'rgba(59,130,246,0.3)' };

const CANCEL_REASONS = ['단순 변심', '배송 지연', '상품 정보 다름', '중복 주문', '가격 오류', '기타'];

function getStepIndex(status: string) {
  return STATUS_STEPS.findIndex(s => s.key === status);
}

export default function OrderDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('단순 변심');
  const [cancelling, setCancelling] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const fetchOrder = useCallback(() => {
    return fetch(`/api/orders/${id}`, { credentials: 'include' })
      .then(r => r.json())
      .then(data => {
        setOrder(data);
        setLastUpdated(new Date());
      })
      .catch(() => setOrder(null));
  }, [id]);

  useEffect(() => {
    fetchOrder().finally(() => setLoading(false));

    const pollId = setInterval(() => {
      if (order && ['PAYMENT_COMPLETED', 'PREPARING', 'SHIPPED'].includes(order.status)) {
        fetchOrder();
      }
    }, 30000);

    return () => clearInterval(pollId);
  }, [fetchOrder]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCancel = async () => {
    if (!order) return;
    setCancelling(true);
    try {
      const res = await fetch(`/api/orders/${order.id}/cancel?reason=${encodeURIComponent(cancelReason)}`, {
        method: 'POST', credentials: 'include',
      });
      if (res.ok) {
        toast.success('주문이 취소됐습니다.');
        setShowCancelModal(false);
        await fetchOrder();
      } else {
        const err = await res.json().catch(() => ({}));
        toast.error(err.message || '주문 취소에 실패했습니다.');
      }
    } catch {
      toast.error('주문 취소에 실패했습니다.');
    }
    setCancelling(false);
  };

  if (loading) return (
    <main className="min-h-screen pb-14 md:pb-0" style={{ background: '#F7F6F1' }}>
      <GlobalNav />
      <div className="max-w-[900px] mx-auto px-4 py-6 space-y-4">
        {[1,2,3].map(i => (
          <div key={i} className="h-32 animate-pulse" style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }} />
        ))}
      </div>
    </main>
  );

  if (!order) return (
    <main className="min-h-screen pb-14 md:pb-0" style={{ background: '#F7F6F1' }}>
      <GlobalNav />
      <div className="max-w-[900px] mx-auto px-4 py-16 text-center">
        <svg className="w-12 h-12 mx-auto mb-4" style={{ color: 'rgba(14,14,14,0.18)' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <h2 className="text-base font-bold mb-4" style={{ color: '#0E0E0E' }}>주문을 찾을 수 없습니다</h2>
        <button
          onClick={() => router.push('/my-orders')}
          className="px-6 py-2 text-white text-sm font-semibold transition-colors"
          style={{ background: '#E8001D' }}
          onMouseEnter={e => (e.currentTarget.style.background = '#C8001A')}
          onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
        >
          주문 목록으로
        </button>
      </div>
    </main>
  );

  const stepIdx = getStepIndex(order.status);
  const isActive = !['CANCELLED', 'RETURN_REQUESTED'].includes(order.status);
  const canCancel = ['PENDING', 'CONFIRMED', 'PAYMENT_PENDING', 'PAYMENT_COMPLETED'].includes(order.status);
  const statusStyle = STATUS_STYLE[order.status as StatusStyleKey] || DEFAULT_STATUS_STYLE;

  return (
    <main className="min-h-screen pb-14 md:pb-0" style={{ background: '#F7F6F1' }}>
      <GlobalNav />
      <div className="max-w-[900px] mx-auto px-4 py-6 space-y-4">
        {/* 헤더 */}
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div>
            <button
              onClick={() => router.push('/my-orders')}
              className="text-sm flex items-center gap-1 mb-2 transition-colors"
              style={{ color: 'rgba(14,14,14,0.45)' }}
              onMouseEnter={e => (e.currentTarget.style.color = '#0E0E0E')}
              onMouseLeave={e => (e.currentTarget.style.color = 'rgba(14,14,14,0.45)')}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
              주문 목록
            </button>
            <h1 className="text-xl font-bold" style={{ color: '#0E0E0E' }}>주문 상세</h1>
            <p className="text-sm mt-1" style={{ color: 'rgba(14,14,14,0.45)' }}>
              {order.orderNumber || `#${order.id}`} &middot; {new Date(order.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
            </p>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <span
              className="text-sm font-semibold px-3 py-1.5"
              style={{ background: statusStyle.bg, color: statusStyle.color, border: `1px solid ${statusStyle.border}` }}
            >
              {STATUS_MAP[order.status] || order.status}
            </span>
            <button
              onClick={() => fetchOrder()}
              className="flex items-center gap-1.5 text-xs transition-colors"
              style={{ color: 'rgba(14,14,14,0.4)' }}
              onMouseEnter={e => (e.currentTarget.style.color = '#0E0E0E')}
              onMouseLeave={e => (e.currentTarget.style.color = 'rgba(14,14,14,0.4)')}
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              {lastUpdated ? `${lastUpdated.getHours().toString().padStart(2,'0')}:${lastUpdated.getMinutes().toString().padStart(2,'0')} 업데이트` : '새로고침'}
            </button>
          </div>
        </div>

        {/* 배송 진행 현황 */}
        {isActive && (
          <div className="p-5" style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }}>
            <h2 className="font-bold text-sm mb-6" style={{ color: '#0E0E0E' }}>배송 현황</h2>
            <div className="relative flex items-center justify-between">
              <div className="absolute left-0 right-0 h-0.5 top-4 z-0" style={{ background: 'rgba(14,14,14,0.1)' }}>
                <div
                  className="h-full transition-all duration-700"
                  style={{
                    background: '#E8001D',
                    width: stepIdx < 0 ? '0%' : `${(stepIdx / (STATUS_STEPS.length - 1)) * 100}%`,
                  }}
                />
              </div>
              {STATUS_STEPS.map((step, i) => {
                const done = i <= stepIdx;
                const current = i === stepIdx;
                return (
                  <div key={step.key} className="relative z-10 flex flex-col items-center gap-2">
                    <div
                      className="w-8 h-8 flex items-center justify-center text-sm font-bold border-2 transition-colors"
                      style={{
                        background: done ? '#E8001D' : '#FFFFFF',
                        borderColor: done ? '#E8001D' : 'rgba(14,14,14,0.2)',
                        color: done ? '#FFFFFF' : 'rgba(14,14,14,0.35)',
                        boxShadow: current ? '0 0 0 4px rgba(232,0,29,0.1)' : 'none',
                      }}
                    >
                      {done ? (
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                        </svg>
                      ) : (
                        <span className="text-xs">{i + 1}</span>
                      )}
                    </div>
                    <span className="text-xs whitespace-nowrap font-medium" style={{ color: done ? '#E8001D' : 'rgba(14,14,14,0.35)' }}>
                      {step.label}
                    </span>
                  </div>
                );
              })}
            </div>
            {order.trackingNumber && (
              <div className="mt-5 p-3 flex items-center justify-between" style={{ background: '#F7F6F1', border: '1px solid rgba(14,14,14,0.07)' }}>
                <span className="text-sm" style={{ color: '#0E0E0E' }}>
                  운송장 번호: <span className="font-semibold">{order.trackingNumber}</span>
                </span>
                <a
                  href={`https://www.cjlogistics.com/ko/tool/parcel/tracking?gnbInvcNo=${order.trackingNumber}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs px-3 py-1.5 transition-colors"
                  style={{ border: '1px solid rgba(14,14,14,0.14)', color: 'rgba(14,14,14,0.65)' }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'rgba(14,14,14,0.04)')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                >
                  배송 조회
                </a>
              </div>
            )}
          </div>
        )}

        {/* 주문 상품 */}
        <div className="p-5" style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }}>
          <h2 className="font-bold text-sm mb-4" style={{ color: '#0E0E0E' }}>주문 상품</h2>
          <div style={{ borderTop: '1px solid rgba(14,14,14,0.06)' }}>
            {(order.items || []).map((item, i) => (
              <div key={i} className="flex items-center gap-4 py-3" style={{ borderBottom: '1px solid rgba(14,14,14,0.06)' }}>
                <button
                  onClick={() => router.push(`/products/${item.productId}`)}
                  className="w-16 h-16 overflow-hidden flex-shrink-0 flex items-center justify-center hover:opacity-80 transition-opacity"
                  style={{ background: '#F5F4F0' }}
                >
                  <svg className="w-7 h-7" style={{ color: 'rgba(14,14,14,0.18)' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                  </svg>
                </button>
                <div className="flex-1">
                  <p
                    className="font-medium text-sm cursor-pointer transition-colors"
                    style={{ color: '#0E0E0E' }}
                    onClick={() => router.push(`/products/${item.productId}`)}
                    onMouseEnter={e => (e.currentTarget.style.color = '#E8001D')}
                    onMouseLeave={e => (e.currentTarget.style.color = '#0E0E0E')}
                  >{item.productName}</p>
                  <p className="text-xs mt-0.5" style={{ color: 'rgba(14,14,14,0.45)' }}>{item.quantity}개 × {Number(item.productPrice).toLocaleString()}원</p>
                </div>
                <p className="font-bold text-sm" style={{ color: '#0E0E0E' }}>{Number(item.totalPrice).toLocaleString()}원</p>
              </div>
            ))}
          </div>

          {/* 금액 요약 */}
          <div className="mt-4 pt-4 space-y-2" style={{ borderTop: '1px solid rgba(14,14,14,0.07)' }}>
            <div className="flex justify-between text-sm" style={{ color: 'rgba(14,14,14,0.6)' }}>
              <span>상품금액</span>
              <span>{Number(order.totalAmount).toLocaleString()}원</span>
            </div>
            <div className="flex justify-between text-sm" style={{ color: 'rgba(14,14,14,0.6)' }}>
              <span>배송비</span>
              <span style={{ color: 'rgb(21,128,61)', fontWeight: 500 }}>무료</span>
            </div>
            <div className="flex justify-between font-bold pt-2" style={{ borderTop: '1px solid rgba(14,14,14,0.07)', color: '#0E0E0E' }}>
              <span>총 결제금액</span>
              <span className="text-lg" style={{ color: '#E8001D' }}>{Number(order.totalAmount).toLocaleString()}원</span>
            </div>
          </div>
        </div>

        <div className="grid sm:grid-cols-2 gap-4">
          {/* 배송 정보 */}
          <div className="p-5" style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }}>
            <h2 className="font-bold text-sm mb-4" style={{ color: '#0E0E0E' }}>배송 정보</h2>
            <div className="space-y-2.5 text-sm">
              <div className="flex gap-3">
                <span className="w-16 flex-shrink-0 text-xs pt-0.5" style={{ color: 'rgba(14,14,14,0.4)' }}>연락처</span>
                <span style={{ color: '#0E0E0E' }}>{order.phoneNumber || '-'}</span>
              </div>
              <div className="flex gap-3">
                <span className="w-16 flex-shrink-0 text-xs pt-0.5" style={{ color: 'rgba(14,14,14,0.4)' }}>주소</span>
                <span style={{ color: '#0E0E0E' }}>{order.deliveryAddress || '-'}</span>
              </div>
              {order.orderNote && (
                <div className="flex gap-3">
                  <span className="w-16 flex-shrink-0 text-xs pt-0.5" style={{ color: 'rgba(14,14,14,0.4)' }}>메모</span>
                  <span style={{ color: '#0E0E0E' }}>{order.orderNote}</span>
                </div>
              )}
            </div>
          </div>

          {/* 결제 정보 */}
          <div className="p-5" style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }}>
            <h2 className="font-bold text-sm mb-4" style={{ color: '#0E0E0E' }}>결제 정보</h2>
            <div className="space-y-2.5 text-sm">
              <div className="flex justify-between">
                <span style={{ color: 'rgba(14,14,14,0.5)' }}>결제 수단</span>
                <span className="font-medium" style={{ color: '#0E0E0E' }}>{order.paymentMethod || '-'}</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'rgba(14,14,14,0.5)' }}>결제 금액</span>
                <span className="font-bold" style={{ color: '#E8001D' }}>{Number(order.totalAmount).toLocaleString()}원</span>
              </div>
            </div>
          </div>
        </div>

        {/* 액션 버튼 */}
        <div className="flex gap-3 flex-wrap">
          {order.status === 'DELIVERED' && (
            <>
              <button
                onClick={() => router.push(`/products/${order.items?.[0]?.productId || ''}`)}
                className="px-5 py-2 text-white text-sm font-semibold transition-colors"
                style={{ background: '#E8001D' }}
                onMouseEnter={e => (e.currentTarget.style.background = '#C8001A')}
                onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
              >
                리뷰 작성
              </button>
              <button
                onClick={() => router.push('/returns')}
                className="px-5 py-2 text-sm font-semibold transition-colors"
                style={{ border: '1px solid rgba(232,0,29,0.3)', color: '#E8001D' }}
                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(232,0,29,0.05)')}
                onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
              >
                반품/교환 신청
              </button>
            </>
          )}
          {canCancel && (
            <button
              onClick={() => setShowCancelModal(true)}
              className="px-5 py-2 text-sm font-semibold transition-colors"
              style={{ border: '1px solid rgba(14,14,14,0.2)', color: '#0E0E0E' }}
              onMouseEnter={e => (e.currentTarget.style.background = 'rgba(14,14,14,0.04)')}
              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
            >
              주문 취소
            </button>
          )}
          <button
            onClick={() => router.push('/my-orders')}
            className="px-5 py-2 text-sm font-medium transition-colors"
            style={{ border: '1px solid rgba(14,14,14,0.12)', color: 'rgba(14,14,14,0.6)' }}
            onMouseEnter={e => (e.currentTarget.style.background = 'rgba(14,14,14,0.04)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          >
            목록으로
          </button>
        </div>
      </div>

      {/* 주문 취소 모달 */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4" style={{ background: 'rgba(0,0,0,0.5)' }}>
          <div className="bg-white w-full max-w-sm p-6 shadow-xl">
            <h3 className="text-lg font-bold mb-4" style={{ color: '#0E0E0E' }}>주문 취소</h3>
            <p className="text-sm mb-4" style={{ color: 'rgba(14,14,14,0.6)' }}>취소 사유를 선택해주세요.</p>
            <div className="space-y-2 mb-6">
              {CANCEL_REASONS.map(reason => (
                <label key={reason} className="flex items-center gap-3 cursor-pointer group">
                  <div
                    className="w-4 h-4 rounded-full border-2 flex items-center justify-center transition-colors"
                    style={{
                      borderColor: cancelReason === reason ? '#E8001D' : 'rgba(14,14,14,0.2)',
                      background: cancelReason === reason ? '#E8001D' : 'transparent',
                    }}
                  >
                    {cancelReason === reason && <div className="w-1.5 h-1.5 bg-white rounded-full" />}
                  </div>
                  <span className="text-sm" style={{ color: '#0E0E0E' }}>{reason}</span>
                  <input type="radio" className="sr-only" value={reason} checked={cancelReason === reason} onChange={() => setCancelReason(reason)} />
                </label>
              ))}
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setShowCancelModal(false)}
                className="flex-1 py-2.5 text-sm font-semibold transition-colors"
                style={{ border: '1px solid rgba(14,14,14,0.14)', color: '#0E0E0E' }}
                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(14,14,14,0.04)')}
                onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
              >
                돌아가기
              </button>
              <button
                onClick={handleCancel}
                disabled={cancelling}
                className="flex-1 py-2.5 text-white text-sm font-semibold transition-colors disabled:opacity-50"
                style={{ background: '#E8001D' }}
                onMouseEnter={e => !cancelling && (e.currentTarget.style.background = '#C8001A')}
                onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
              >
                {cancelling ? '취소 중...' : '주문 취소'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
