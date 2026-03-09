'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { GlobalNav } from '@/components/GlobalNav';

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

function getStepIndex(status: string) {
  return STATUS_STEPS.findIndex(s => s.key === status);
}

export default function OrderDetailPage() {
  const { id } = useParams();
  const router = useRouter();
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`/api/orders/${id}`, { credentials: 'include' })
      .then(r => r.json())
      .then(setOrder)
      .catch(() => setOrder(null))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return (
    <main className="min-h-screen bg-gray-100 pb-14 md:pb-0">
      <GlobalNav />
      <div className="max-w-[1280px] mx-auto px-4 py-6 space-y-4">
        {[1,2,3].map(i => <div key={i} className="bg-white rounded-xl h-32 animate-pulse border border-gray-100"/>)}
      </div>
    </main>
  );

  if (!order) return (
    <main className="min-h-screen bg-gray-100 pb-14 md:pb-0">
      <GlobalNav />
      <div className="max-w-[1280px] mx-auto px-4 py-16 text-center">
        <div className="text-5xl mb-4">❌</div>
        <h2 className="text-xl font-bold text-gray-900 mb-4">주문을 찾을 수 없습니다</h2>
        <button onClick={() => router.push('/my-orders')} className="btn-primary px-6">주문 목록으로</button>
      </div>
    </main>
  );

  const stepIdx = getStepIndex(order.status);
  const isActive = !['CANCELLED', 'RETURN_REQUESTED'].includes(order.status);

  return (
    <main className="min-h-screen bg-gray-100 pb-14 md:pb-0">
      <GlobalNav />
      <div className="max-w-[900px] mx-auto px-4 py-6 space-y-4">
        {/* 헤더 */}
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <button onClick={() => router.push('/my-orders')} className="text-sm text-gray-500 hover:text-red-600 flex items-center gap-1 mb-2 transition-colors">
              ← 주문 목록
            </button>
            <h1 className="text-2xl font-bold text-gray-900">주문 상세</h1>
            <p className="text-sm text-gray-500 mt-1">
              주문번호: {order.orderNumber || `#${order.id}`} · {new Date(order.createdAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })}
            </p>
          </div>
          <span className={`text-sm font-semibold px-3 py-1.5 rounded-full ${
            order.status === 'DELIVERED' ? 'bg-green-100 text-green-700'
            : order.status === 'CANCELLED' ? 'bg-gray-100 text-gray-600'
            : order.status === 'SHIPPED' ? 'bg-purple-100 text-purple-700'
            : 'bg-blue-100 text-blue-700'
          }`}>
            {STATUS_MAP[order.status] || order.status}
          </span>
        </div>

        {/* 배송 진행 상황 */}
        {isActive && (
          <div className="bg-white rounded-xl border border-gray-100 p-5">
            <h2 className="font-bold text-gray-900 mb-5">배송 현황</h2>
            <div className="relative flex items-center justify-between">
              <div className="absolute left-0 right-0 h-1 bg-gray-100 top-4 z-0">
                <div className="h-full bg-red-500 transition-all duration-700"
                  style={{ width: stepIdx < 0 ? '0%' : `${(stepIdx / (STATUS_STEPS.length - 1)) * 100}%` }} />
              </div>
              {STATUS_STEPS.map((step, i) => {
                const done = i <= stepIdx;
                const current = i === stepIdx;
                return (
                  <div key={step.key} className="relative z-10 flex flex-col items-center gap-2">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold border-2 transition-colors ${
                      done ? 'bg-red-600 border-red-600 text-white' : 'bg-white border-gray-200 text-gray-400'
                    } ${current ? 'ring-4 ring-red-100' : ''}`}>
                      {done ? '✓' : i + 1}
                    </div>
                    <span className={`text-xs whitespace-nowrap font-medium ${done ? 'text-red-600' : 'text-gray-400'}`}>{step.label}</span>
                  </div>
                );
              })}
            </div>
            {order.trackingNumber && (
              <div className="mt-5 p-3 bg-gray-50 rounded-lg flex items-center justify-between">
                <span className="text-sm text-gray-700">운송장 번호: <span className="font-semibold">{order.trackingNumber}</span></span>
                <button onClick={() => router.push(`/delivery/${order.trackingNumber}`)}
                  className="text-xs btn-outline-red px-3 py-1">배송 조회</button>
              </div>
            )}
          </div>
        )}

        {/* 주문 상품 */}
        <div className="bg-white rounded-xl border border-gray-100 p-5">
          <h2 className="font-bold text-gray-900 mb-4">주문 상품</h2>
          <div className="space-y-3">
            {(order.items || []).map((item, i) => (
              <div key={i} className="flex items-center gap-4 py-3 border-b border-gray-50 last:border-0">
                <div className="w-16 h-16 rounded-lg bg-gray-100 overflow-hidden flex-shrink-0 flex items-center justify-center text-2xl">
                  📦
                </div>
                <div className="flex-1">
                  <p className="font-medium text-gray-900">{item.productName}</p>
                  <p className="text-sm text-gray-500">{item.quantity}개 × {Number(item.productPrice).toLocaleString()}원</p>
                </div>
                <p className="font-bold text-gray-900">{Number(item.totalPrice).toLocaleString()}원</p>
              </div>
            ))}
          </div>
          <div className="mt-4 pt-4 border-t border-gray-100 flex justify-between">
            <span className="font-bold text-gray-900">총 결제금액</span>
            <span className="text-xl font-bold text-red-600">{Number(order.totalAmount).toLocaleString()}원</span>
          </div>
        </div>

        <div className="grid sm:grid-cols-2 gap-4">
          {/* 배송 정보 */}
          <div className="bg-white rounded-xl border border-gray-100 p-5">
            <h2 className="font-bold text-gray-900 mb-3">배송 정보</h2>
            <div className="space-y-2 text-sm">
              <div className="flex gap-3"><span className="text-gray-400 w-16 flex-shrink-0">연락처</span><span className="text-gray-900">{order.phoneNumber || '-'}</span></div>
              <div className="flex gap-3"><span className="text-gray-400 w-16 flex-shrink-0">주소</span><span className="text-gray-900">{order.deliveryAddress || '-'}</span></div>
              {order.orderNote && <div className="flex gap-3"><span className="text-gray-400 w-16 flex-shrink-0">메모</span><span className="text-gray-900">{order.orderNote}</span></div>}
            </div>
          </div>
          {/* 결제 정보 */}
          <div className="bg-white rounded-xl border border-gray-100 p-5">
            <h2 className="font-bold text-gray-900 mb-3">결제 정보</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between"><span className="text-gray-500">결제 수단</span><span className="font-medium">{order.paymentMethod || '-'}</span></div>
              <div className="flex justify-between"><span className="text-gray-500">결제 금액</span><span className="font-bold text-red-600 text-base">{Number(order.totalAmount).toLocaleString()}원</span></div>
            </div>
          </div>
        </div>

        {/* 액션 */}
        <div className="flex gap-3 flex-wrap">
          {order.status === 'DELIVERED' && (
            <>
              <button className="btn-primary px-5">리뷰 작성</button>
              <button onClick={() => router.push('/returns')} className="btn-outline-red px-5">반품/교환 신청</button>
            </>
          )}
          {['PENDING','CONFIRMED','PAYMENT_PENDING'].includes(order.status) && (
            <button className="px-5 py-2 border border-red-300 text-red-600 rounded-xl hover:bg-red-50 font-medium transition-colors">주문 취소</button>
          )}
          <button onClick={() => router.push('/my-orders')}
            className="px-5 py-2 border border-gray-200 text-gray-700 rounded-xl hover:bg-gray-50 font-medium transition-colors">
            목록으로
          </button>
        </div>
      </div>
    </main>
  );
}
