'use client';

import { useState, useEffect } from 'react';

const API_BASE = '';

export default function OnboardingPage() {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreeMarketing, setAgreeMarketing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const savedName = localStorage.getItem('userName');
    if (savedName) setName(savedName);
  }, []);

  // 010-XXXX-XXXX 형식 자동 포맷
  const formatPhone = (value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  };

  const handlePhoneChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setPhone(formatPhone(e.target.value));
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!agreeTerms) {
      setError('서비스 이용약관에 동의해주세요.');
      return;
    }
    const rawPhone = phone.replace(/-/g, '');
    if (rawPhone.length < 10 || !rawPhone.startsWith('01')) {
      setError('올바른 휴대폰 번호를 입력해주세요. (예: 010-1234-5678)');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${API_BASE}/api/users/me`, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name, phoneNumber: phone }),
      });

      if (res.ok) {
        const data = await res.json();
        if (data.name) localStorage.setItem('userName', data.name);
        window.location.href = '/';
      } else if (res.status === 401) {
        window.location.href = '/auth';
      } else {
        const body = await res.text().catch(() => '');
        setError(`정보 저장에 실패했습니다. (${res.status}) ${body.slice(0, 120)}`);
      }
    } catch {
      setError('서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요.');
    }

    setLoading(false);
  };

  const handleSkip = () => {
    window.location.href = '/';
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-10" style={{ background: '#F7F6F1' }}>
      <div className="w-full max-w-md">

        {/* 로고 */}
        <div className="text-center mb-8">
          <a href="/" className="inline-block mb-4">
            <span className="text-3xl font-black tracking-tight">
              <span className="text-red-600">Live</span>
              <span style={{ color: '#0E0E0E' }}>Mart</span>
            </span>
          </a>
          {/* 진행 단계 표시 */}
          <div className="flex items-center justify-center gap-2 mb-5">
            <div className="w-8 h-1.5" style={{ background: 'rgba(14,14,14,0.12)' }} />
            <div className="w-8 h-1.5" style={{ background: '#E8001D' }} />
          </div>
          <h1 className="text-2xl font-bold" style={{ color: '#0E0E0E' }}>추가 정보 입력</h1>
          <p className="text-sm mt-1.5" style={{ color: 'rgba(14,14,14,0.5)' }}>
            원활한 쇼핑을 위해 아래 정보를 입력해주세요
          </p>
        </div>

        {/* 폼 카드 */}
        <form
          onSubmit={handleSubmit}
          className="shadow-sm p-8 space-y-5"
          style={{ background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.07)' }}
        >
          <div>
            <label className="block text-sm font-semibold mb-1.5" style={{ color: 'rgba(14,14,14,0.65)' }}>이름</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="홍길동"
              className="w-full px-4 py-3 text-sm focus:outline-none transition"
              style={{ border: '1px solid rgba(14,14,14,0.14)', color: '#0E0E0E' }}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1.5" style={{ color: 'rgba(14,14,14,0.65)' }}>
              휴대폰 번호 <span style={{ color: '#E8001D' }}>*</span>
            </label>
            <input
              type="tel"
              value={phone}
              onChange={handlePhoneChange}
              placeholder="010-0000-0000"
              maxLength={13}
              inputMode="numeric"
              className="w-full px-4 py-3 text-sm focus:outline-none transition"
              style={{ border: '1px solid rgba(14,14,14,0.14)', color: '#0E0E0E' }}
              required
            />
            <p className="text-xs mt-1" style={{ color: 'rgba(14,14,14,0.4)' }}>배송지 확인 및 본인 인증에 사용됩니다</p>
          </div>

          <div className="p-4 space-y-3" style={{ background: '#F7F6F1' }}>
            <p className="text-sm font-semibold" style={{ color: '#0E0E0E' }}>약관 동의</p>

            <label className="flex items-center gap-3 cursor-pointer group">
              <input
                type="checkbox"
                checked={agreeTerms && agreeMarketing}
                onChange={(e) => { setAgreeTerms(e.target.checked); setAgreeMarketing(e.target.checked); }}
                className="w-4 h-4 accent-stone-800 cursor-pointer"
              />
              <span className="text-sm font-medium transition-colors" style={{ color: '#0E0E0E' }}>전체 동의</span>
            </label>

            <div className="pt-3 space-y-2.5" style={{ borderTop: '1px solid rgba(14,14,14,0.1)' }}>
              <label className="flex items-start gap-3 cursor-pointer">
                <input type="checkbox" checked={agreeTerms} onChange={(e) => setAgreeTerms(e.target.checked)} className="mt-0.5 w-4 h-4 accent-stone-800 cursor-pointer" />
                <span className="text-sm" style={{ color: 'rgba(14,14,14,0.6)' }}>
                  <span className="font-medium" style={{ color: '#E8001D' }}>[필수] </span>
                  서비스 이용약관 및 개인정보처리방침 동의
                </span>
              </label>
              <label className="flex items-start gap-3 cursor-pointer">
                <input type="checkbox" checked={agreeMarketing} onChange={(e) => setAgreeMarketing(e.target.checked)} className="mt-0.5 w-4 h-4 accent-stone-800 cursor-pointer" />
                <span className="text-sm" style={{ color: 'rgba(14,14,14,0.6)' }}>
                  <span style={{ color: 'rgba(14,14,14,0.35)' }}>[선택] </span>
                  마케팅 정보 수신 동의 (이벤트·할인·혜택 안내)
                </span>
              </label>
            </div>
          </div>

          {error && (
            <div className="px-4 py-3" style={{ background: 'rgba(232,0,29,0.06)', border: '1px solid rgba(232,0,29,0.2)' }}>
              <p className="text-sm" style={{ color: '#E8001D' }}>{error}</p>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 text-white font-semibold transition-colors disabled:opacity-60 disabled:cursor-not-allowed text-sm"
            style={{ background: '#E8001D' }}
            onMouseEnter={e => !loading && (e.currentTarget.style.background = '#C8001A')}
            onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                저장 중...
              </span>
            ) : (
              'LiveMart 시작하기 →'
            )}
          </button>

          <button
            type="button"
            onClick={handleSkip}
            className="w-full py-2 text-xs transition-colors"
            style={{ color: 'rgba(14,14,14,0.4)' }}
            onMouseEnter={e => (e.currentTarget.style.color = '#0E0E0E')}
            onMouseLeave={e => (e.currentTarget.style.color = 'rgba(14,14,14,0.4)')}
          >
            나중에 입력할게요
          </button>
        </form>

        <p className="text-center text-xs mt-5" style={{ color: 'rgba(14,14,14,0.4)' }}>
          입력하신 정보는 안전하게 보호됩니다
        </p>
      </div>
    </div>
  );
}
