'use client';

import { useState, useEffect } from 'react';
import { GlobalNav } from '@/components/GlobalNav';
import toast from 'react-hot-toast';

interface ReturnItem {
  id: number;
  returnNumber?: string;
  orderId: number;
  orderNumber?: string;
  status: string;
  reason: string;
  reasonDetail?: string;
  refundAmount?: number;
  requestedAt: string;
}

// 백엔드 enum 값에 맞춘 사유 목록
const REASON_OPTIONS = [
  { value: 'CHANGED_MIND',       label: '단순 변심' },
  { value: 'DEFECTIVE',          label: '상품 불량/하자' },
  { value: 'WRONG_ITEM',         label: '오배송' },
  { value: 'NOT_AS_DESCRIBED',   label: '상품 설명과 다름' },
  { value: 'SIZE_ISSUE',         label: '사이즈 문제' },
  { value: 'LATE_DELIVERY',      label: '배송 지연' },
  { value: 'OTHER',              label: '기타' },
];

const RETURN_STATUS: Record<string, { label: string; color: string }> = {
  REQUESTED:  { label: '신청 완료',   color: 'badge-blue' },
  APPROVED:   { label: '승인됨',      color: 'badge-green' },
  REJECTED:   { label: '반려됨',      color: 'badge-red' },
  IN_TRANSIT: { label: '반품 배송중', color: 'badge-purple' },
  RECEIVED:   { label: '수령 완료',   color: 'badge-blue' },
  COMPLETED:  { label: '반품 완료',   color: 'badge-gray' },
  CANCELLED:  { label: '취소됨',      color: 'badge-gray' },
};

const REASON_LABEL: Record<string, string> = Object.fromEntries(
  REASON_OPTIONS.map(r => [r.value, r.label])
);

export default function ReturnsPage() {
  const [tab, setTab] = useState<'list' | 'new'>('list');
  const [returns, setReturns] = useState<ReturnItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({
    orderId: '',
    returnType: 'RETURN',   // RETURN | EXCHANGE | REFUND
    reason: REASON_OPTIONS[0].value,
    detail: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const userId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

  const fetchReturns = () => {
    if (!userId) return;
    fetch(`/api/returns/user/${userId}`, { credentials: 'include' })
      .then(r => r.json())
      .then(d => setReturns(d.content || d || []))
      .catch(() => {});
  };

  useEffect(() => {
    if (!userId) { setLoading(false); return; }
    fetchReturns();
    setLoading(false);
  }, [userId]);

  const submitReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.orderId) { toast.error('주문 번호를 입력하세요'); return; }
    setSubmitting(true);
    try {
      const res = await fetch('/api/returns', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderId: Number(form.orderId),
          userId: Number(userId),
          returnType: form.returnType,
          reason: form.reason,
          reasonDetail: form.detail || null,
        }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || err.detail || '신청 실패');
      }
      toast.success('반품/교환 신청이 완료되었습니다');
      setTab('list');
      setForm({ orderId: '', returnType: 'RETURN', reason: REASON_OPTIONS[0].value, detail: '' });
      fetchReturns();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : '오류 발생');
    }
    setSubmitting(false);
  };

  return (
    <main className="min-h-screen bg-gray-100 pb-14 md:pb-0">
      <GlobalNav />
      <div className="max-w-[700px] mx-auto px-4 py-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">반품/교환</h1>

        <div className="flex gap-2 mb-5">
          {[{ id: 'list', label: '신청 내역' }, { id: 'new', label: '반품/교환 신청' }].map(t => (
            <button key={t.id} onClick={() => setTab(t.id as 'list' | 'new')}
              className={`px-5 py-2 rounded-xl text-sm font-semibold border transition-colors ${tab === t.id ? 'bg-red-600 text-white border-red-600' : 'bg-white text-gray-700 border-gray-200 hover:border-red-300'}`}>
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'list' ? (
          loading ? (
            <div className="space-y-3">{[1,2].map(i => <div key={i} className="bg-white rounded-xl h-24 animate-pulse border border-gray-100"/>)}</div>
          ) : returns.length === 0 ? (
            <div className="text-center py-20 bg-white rounded-2xl border border-gray-100">
              <div className="text-5xl mb-4">↩️</div>
              <h2 className="text-lg font-bold text-gray-900 mb-2">신청 내역이 없습니다</h2>
              <button onClick={() => setTab('new')} className="btn-primary px-5 mt-4">반품/교환 신청하기</button>
            </div>
          ) : (
            <div className="space-y-3">
              {returns.map(r => {
                const st = RETURN_STATUS[r.status] || { label: r.status, color: 'badge-gray' };
                return (
                  <div key={r.id} className="bg-white rounded-xl border border-gray-100 p-5">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm text-gray-500">
                        {r.returnNumber || `주문 #${r.orderId}`}
                      </span>
                      <span className={`${st.color} text-xs`}>{st.label}</span>
                    </div>
                    <p className="text-sm font-medium text-gray-900">{REASON_LABEL[r.reason] || r.reason}</p>
                    {r.reasonDetail && <p className="text-xs text-gray-500 mt-1">{r.reasonDetail}</p>}
                    {r.refundAmount && (
                      <p className="text-sm text-red-600 font-semibold mt-1">환불 예정: {Number(r.refundAmount).toLocaleString()}원</p>
                    )}
                    <p className="text-xs text-gray-400 mt-1">신청일: {new Date(r.requestedAt).toLocaleDateString('ko-KR')}</p>
                  </div>
                );
              })}
            </div>
          )
        ) : (
          <form onSubmit={submitReturn} className="bg-white rounded-xl border border-gray-100 p-6 space-y-4">
            <h2 className="font-bold text-gray-900 text-lg">반품/교환 신청</h2>
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm text-yellow-700">
              ⚠️ 배송 완료 후 7일 이내에만 신청 가능합니다
            </div>
            <div>
              <label className="form-label">주문 번호 *</label>
              <input type="number" value={form.orderId}
                onChange={e => setForm(f => ({ ...f, orderId: e.target.value }))}
                placeholder="주문 번호 입력 (예: 123)" className="form-input" required />
            </div>
            <div>
              <label className="form-label">신청 유형 *</label>
              <div className="grid grid-cols-3 gap-2">
                {[{ v: 'RETURN', l: '반품' }, { v: 'EXCHANGE', l: '교환' }, { v: 'REFUND', l: '환불만' }].map(t => (
                  <button key={t.v} type="button" onClick={() => setForm(f => ({ ...f, returnType: t.v }))}
                    className={`py-2.5 rounded-xl border-2 text-sm font-semibold transition-colors ${form.returnType === t.v ? 'border-red-500 bg-red-50 text-red-700' : 'border-gray-200 text-gray-600 hover:border-red-200'}`}>
                    {t.l}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="form-label">반품/교환 사유 *</label>
              <select value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} className="form-input">
                {REASON_OPTIONS.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </div>
            <div>
              <label className="form-label">상세 내용</label>
              <textarea value={form.detail} onChange={e => setForm(f => ({ ...f, detail: e.target.value }))}
                placeholder="상세한 사유를 입력해주세요" rows={4} className="form-input resize-none" />
            </div>
            <button type="submit" disabled={submitting || !userId} className="w-full btn-primary py-3 font-bold disabled:opacity-60">
              {!userId ? '로그인이 필요합니다' : submitting ? '신청중...' : '반품/교환 신청'}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}
