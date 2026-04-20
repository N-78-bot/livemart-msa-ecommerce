import { NextRequest, NextResponse } from 'next/server';
import Anthropic from '@anthropic-ai/sdk';

const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

const SYSTEM_PROMPT = `당신은 LiveMart의 AI 고객 상담원입니다.
LiveMart는 전자기기, 패션, 식품, 홈/리빙, 뷰티, 스포츠 카테고리의 프리미엄 쇼핑몰입니다.
주문 조회, 배송 문의, 상품 정보, 반품/교환 정책, 쿠폰 사용법 등을 친절하고 간결하게 안내하세요.
한국어로 답변하고, 이모지를 적절히 사용하세요. 모르는 정보는 솔직하게 모른다고 하세요.`;

export async function POST(request: NextRequest) {
  try {
    const { message, history = [] } = await request.json();

    if (!message) {
      return NextResponse.json({ error: '메시지가 없습니다.' }, { status: 400 });
    }

    const messages: Anthropic.MessageParam[] = [
      ...history.map((h: { role: string; content: string }) => ({
        role: h.role as 'user' | 'assistant',
        content: h.content,
      })),
      { role: 'user', content: message },
    ];

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      async start(controller) {
        try {
          const response = await client.messages.create({
            model: 'claude-haiku-4-5-20251001',
            max_tokens: 512,
            system: SYSTEM_PROMPT,
            messages,
            stream: true,
          });

          for await (const event of response) {
            if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
              controller.enqueue(encoder.encode(`data:${event.delta.text}\n`));
            }
          }
          controller.enqueue(encoder.encode('data:[DONE]\n'));
        } catch (e) {
          console.error('[AI stream error]', e);
          controller.enqueue(encoder.encode('data:[ERROR]\n'));
          controller.enqueue(encoder.encode('data:[DONE]\n'));
        } finally {
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'X-Accel-Buffering': 'no',
      },
    });
  } catch {
    return NextResponse.json({ error: '서버 오류가 발생했습니다.' }, { status: 500 });
  }
}
