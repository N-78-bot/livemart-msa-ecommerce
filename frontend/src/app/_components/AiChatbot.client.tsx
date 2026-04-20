'use client';

import { useState, useRef, useEffect, useCallback } from 'react';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ChatResponse {
  sessionId: string;
  message: string;
  intent: string;
  escalateToHuman: boolean;
}

/**
 * AI CS 챗봇 위젯
 * 우하단 플로팅 버튼 → 슬라이드업 채팅 패널
 * - SSE 스트리밍 지원 (토큰 단위 실시간 출력)
 * - 대화 히스토리 유지 (sessionId 기반)
 * - 주문 컨텍스트 자동 삽입
 */
export function AiChatbot() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      role: 'assistant',
      content: '안녕하세요! LiveMart AI 상담원입니다. 주문 조회, 배송 문의, 상품 정보 등 무엇이든 도와드릴게요.',
      timestamp: new Date(),
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const sendMessage = async () => {
    const text = input.trim();
    if (!text || loading) return;

    const userMessage: Message = { role: 'user', content: text, timestamp: new Date() };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    // AI 응답 자리 미리 추가 (스트리밍 효과)
    const assistantMessage: Message = { role: 'assistant', content: '', timestamp: new Date() };
    setMessages(prev => [...prev, assistantMessage]);

    try {
      const userId = typeof window !== 'undefined' ? localStorage.getItem('userId') : null;

      // SSE 스트리밍 호출 — Next.js rewrite → api-gateway(8888)
      const response = await fetch('/api/ai/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          userId: userId ? parseInt(userId) : null,
          message: text,
          sessionId,
          history: messages.slice(-6).map(m => ({ role: m.role, content: m.content })),
        }),
      });

      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let accumulated = '';
      let sseBuffer = ''; // 미완성 SSE 라인 버퍼

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        sseBuffer += decoder.decode(value, { stream: true });

        // 완성된 SSE 라인만 처리 (개행 기준 분리)
        const lines = sseBuffer.split('\n');
        sseBuffer = lines.pop() ?? ''; // 마지막 미완성 라인은 버퍼에 보관

        let hasNewContent = false;
        for (const line of lines) {
          if (line.startsWith('data:')) {
            // "data:" 접두사(5자) 제거 → 실제 토큰 추출
            const token = line.slice(5);
            if (token === '[DONE]') continue;
            accumulated += token;
            hasNewContent = true;
          }
        }

        if (hasNewContent) {
          setMessages(prev => {
            const updated = [...prev];
            updated[updated.length - 1] = {
              ...updated[updated.length - 1],
              content: accumulated,
            };
            return updated;
          });
        }
      }

      // 스트림 종료 후 content가 비어있으면 에러 메시지로 교체
      if (!accumulated) {
        setMessages(prev => {
          const updated = [...prev];
          updated[updated.length - 1] = {
            ...updated[updated.length - 1],
            content: '죄송합니다. AI 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
          };
          return updated;
        });
      }

      // 세션 ID가 없으면 서버에서 새로 받기
      if (!sessionId) {
        const nonStreamRes = await fetch('/api/ai/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({
            userId: userId ? parseInt(userId) : null,
            message: 'ping',
            sessionId: null,
          }),
        });
        if (nonStreamRes.ok) {
          const data: ChatResponse = await nonStreamRes.json();
          setSessionId(data.sessionId);
        }
      }
    } catch (err) {
      // 에러 시 fallback 메시지
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ...updated[updated.length - 1],
          content: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
        };
        return updated;
      });
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <>
      {/* ── 플로팅 버튼 ── */}
      <button
        onClick={() => setOpen(o => !o)}
        className="fixed bottom-6 right-6 z-50 w-14 h-14 text-white rounded-full shadow-lg flex items-center justify-center transition-all duration-200 hover:scale-110 active:scale-95"
        style={{ background: '#E8001D' }}
        onMouseEnter={e => (e.currentTarget.style.background = '#C8001A')}
        onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
        aria-label="AI 상담원 열기"
      >
        {open ? (
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
          </svg>
        )}
        {/* 알림 배지 */}
        {!open && (
          <span className="absolute -top-1 -right-1 w-4 h-4 bg-green-400 rounded-full border-2 border-white" />
        )}
      </button>

      {/* ── 챗봇 패널 ── */}
      <div
        className={`fixed bottom-24 right-6 z-50 w-80 sm:w-96 flex flex-col transition-all duration-300 ${
          open ? 'opacity-100 translate-y-0 pointer-events-auto' : 'opacity-0 translate-y-4 pointer-events-none'
        }`}
        style={{ height: '480px', background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.12)', boxShadow: '0 8px 32px rgba(14,14,14,0.14)' }}
      >
        {/* 헤더 */}
        <div className="text-white px-4 py-3 flex items-center gap-3 flex-shrink-0" style={{ background: '#0A0A0A' }}>
          <div className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0" style={{ background: '#E8001D' }}>
            <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
          </div>
          <div>
            <div className="font-semibold text-sm">LiveMart AI 상담원</div>
            <div className="text-green-400 text-xs flex items-center gap-1">
              <span className="w-1.5 h-1.5 bg-green-400 rounded-full inline-block" />
              온라인 · 즉시 응답
            </div>
          </div>
        </div>

        {/* 메시지 영역 */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3" style={{ background: '#F7F6F1' }}>
          {messages.map((msg, i) => (
            <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              {msg.role === 'assistant' && (
                <div className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 mr-2 mt-0.5" style={{ background: '#0A0A0A' }}>
                  <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
                  </svg>
                </div>
              )}
              <div
                className={`max-w-[75%] px-3 py-2 text-sm leading-relaxed ${
                  msg.role === 'user' ? 'text-white' : ''
                }`}
                style={msg.role === 'user'
                  ? { background: '#E8001D', borderRadius: '12px 12px 2px 12px', color: '#FFFFFF' }
                  : { background: '#FFFFFF', border: '1px solid rgba(14,14,14,0.08)', borderRadius: '12px 12px 12px 2px', color: '#0E0E0E', boxShadow: '0 1px 3px rgba(14,14,14,0.06)' }
                }
              >
                {msg.content || (
                  <span className="inline-flex gap-1">
                    <span className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: 'rgba(14,14,14,0.35)', animationDelay: '0ms' }} />
                    <span className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: 'rgba(14,14,14,0.35)', animationDelay: '150ms' }} />
                    <span className="w-1.5 h-1.5 rounded-full animate-bounce" style={{ background: 'rgba(14,14,14,0.35)', animationDelay: '300ms' }} />
                  </span>
                )}
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        {/* 입력 영역 */}
        <div className="px-3 py-3 flex gap-2 flex-shrink-0" style={{ borderTop: '1px solid rgba(14,14,14,0.08)', background: '#FFFFFF' }}>
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="메시지를 입력하세요..."
            disabled={loading}
            className="flex-1 px-3 py-2 text-sm focus:outline-none disabled:opacity-50"
            style={{ background: '#F7F6F1', border: '1px solid rgba(14,14,14,0.1)', color: '#0E0E0E' }}
          />
          <button
            onClick={sendMessage}
            disabled={loading || !input.trim()}
            className="w-9 h-9 text-white flex items-center justify-center flex-shrink-0 transition-colors disabled:opacity-40"
            style={{ background: '#E8001D' }}
            onMouseEnter={e => !loading && (e.currentTarget.style.background = '#C8001A')}
            onMouseLeave={e => (e.currentTarget.style.background = '#E8001D')}
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </button>
        </div>
      </div>
    </>
  );
}
