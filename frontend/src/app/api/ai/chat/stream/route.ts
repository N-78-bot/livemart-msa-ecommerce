import { NextRequest, NextResponse } from 'next/server';

const SYSTEM_PROMPT = `당신은 LiveMart의 AI 고객 상담원입니다.
LiveMart는 전자기기, 패션, 식품, 홈/리빙, 뷰티, 스포츠 카테고리의 프리미엄 쇼핑몰입니다.
주문 조회, 배송 문의, 상품 정보, 반품/교환 정책, 쿠폰 사용법 등을 친절하고 간결하게 안내하세요.
한국어로 답변하고, 이모지를 적절히 사용하세요. 모르는 정보는 솔직하게 모른다고 하세요.`;

export async function POST(request: NextRequest) {
  const apiKey = process.env.GROQ_API_KEY;
  console.log('[AI stream] called — key present:', !!apiKey);
  try {
    const { message, history = [] } = await request.json();

    if (!message) {
      return NextResponse.json({ error: '메시지가 없습니다.' }, { status: 400 });
    }

    const trimmedHistory: { role: string; content: string }[] = [...history];
    while (trimmedHistory.length > 0 && trimmedHistory[0].role === 'assistant') {
      trimmedHistory.shift();
    }

    const messages = [
      { role: 'system', content: SYSTEM_PROMPT },
      ...trimmedHistory.map((h: { role: string; content: string }) => ({
        role: h.role,
        content: h.content,
      })),
      { role: 'user', content: message },
    ];

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      async start(controller) {
        try {
          const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${apiKey}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              model: 'llama-3.1-8b-instant',
              messages,
              stream: true,
              max_tokens: 512,
            }),
          });

          if (!res.ok || !res.body) {
            const errText = await res.text();
            throw new Error(`Groq ${res.status}: ${errText}`);
          }

          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';

            for (const line of lines) {
              if (line.startsWith('data: ')) {
                const data = line.slice(6);
                if (data === '[DONE]') continue;
                try {
                  const parsed = JSON.parse(data);
                  const token = parsed.choices?.[0]?.delta?.content;
                  if (token) {
                    controller.enqueue(encoder.encode(`data:${token}\n`));
                  }
                } catch {
                  // skip malformed JSON
                }
              }
            }
          }

          console.log('[AI stream] done');
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
  } catch (e) {
    console.error('[AI stream] outer error:', e);
    return NextResponse.json({ error: '서버 오류가 발생했습니다.' }, { status: 500 });
  }
}
