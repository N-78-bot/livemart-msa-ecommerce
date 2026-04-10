import { NextRequest, NextResponse } from 'next/server';

// /api/ai/chat (non-streaming) — sessionId 발급용 fallback
export async function POST(_request: NextRequest) {
  return NextResponse.json({
    sessionId: `session-${Date.now()}`,
    message: '',
    intent: 'general',
    escalateToHuman: false,
  });
}
