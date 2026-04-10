/**
 * API 캐치올 핸들러
 * 1순위: 실제 백엔드(GCP)로 프록시
 * 2순위: 백엔드 미연결 시 데모 데이터 반환 (fallback)
 */
import { NextRequest, NextResponse } from 'next/server';

// ─────────────────────────────────────────────────────────────────
// 백엔드 프록시
// ─────────────────────────────────────────────────────────────────
const GCP_API = 'http://34.64.189.54:8888';
const BACKEND = (() => {
  const url = (process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || GCP_API).replace(/\/$/, '');
  // localhost/내부 호스트는 Vercel에서 접근 불가 → 데모 모드
  return /localhost|127\.0\.0\.1|\.internal/.test(url) ? null : url;
})();

async function proxyRequest(request: NextRequest, method: string, seg: string[]): Promise<NextResponse | null> {
  if (!BACKEND) return null;
  try {
    const search = request.nextUrl.search;
    const url = `${BACKEND}/api/${seg.join('/')}${search}`;
    const headers: Record<string, string> = {};
    const cookie = request.headers.get('cookie');
    if (cookie) headers['cookie'] = cookie;
    const contentType = request.headers.get('content-type');
    if (contentType) headers['content-type'] = contentType;
    const authorization = request.headers.get('authorization');
    if (authorization) headers['authorization'] = authorization;
    let body: ArrayBuffer | undefined;
    if (!['GET', 'HEAD'].includes(method)) body = await request.arrayBuffer();
    const res = await fetch(url, { method, headers, body, signal: AbortSignal.timeout(8000) });
    const resBody = await res.arrayBuffer();
    const response = new NextResponse(resBody, { status: res.status });
    res.headers.forEach((v, k) => {
      if (!['transfer-encoding', 'connection', 'content-encoding'].includes(k.toLowerCase())) {
        response.headers.set(k, v);
      }
    });
    return response;
  } catch {
    return null; // 백엔드 미연결 → 데모 fallback
  }
}

// ─────────────────────────────────────────────────────────────────
// 데모 데이터
// ─────────────────────────────────────────────────────────────────

const DEMO_PRODUCTS = [
  { id: 1, name: '무선 블루투스 이어폰 ANC Pro', price: 89000, stockQuantity: 42, categoryName: '전자기기', category: '전자기기', description: '액티브 노이즈 캔슬링으로 완벽한 몰입감. 30시간 배터리, IPX5 방수, 멀티포인트 연결 지원.', imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 2, name: '프리미엄 요가 매트 8mm', price: 35000, stockQuantity: 28, categoryName: '스포츠', category: '스포츠', description: '천연 TPE 소재, 미끄럼 방지 텍스처, 친환경 인증 제품.', imageUrl: 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 3, name: '한방 갈비탕 밀키트 2인분', price: 18500, stockQuantity: 65, categoryName: '식품', category: '식품', description: '15시간 우린 진한 사골 육수, 손질된 갈비 400g 포함. 냉장 배송.', imageUrl: 'https://images.unsplash.com/photo-1547592180-85f173990554?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 4, name: '스마트 체중계 블루투스', price: 45000, stockQuantity: 19, categoryName: '건강', category: '건강', description: 'BMI·체지방·근육량·수분 8가지 지표 측정, 앱 연동.', imageUrl: 'https://images.unsplash.com/photo-1576678927484-cc907957088c?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 5, name: '럭셔리 코튼 침구 세트 퀸', price: 128000, stockQuantity: 11, categoryName: '홈/인테리어', category: '홈/인테리어', description: '이집트면 400수 장섬유, 호텔급 부드러움, 사계절 사용 가능.', imageUrl: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 6, name: '캐리어 28인치 하드케이스', price: 89000, stockQuantity: 34, categoryName: '여행', category: '여행', description: 'TSA 잠금장치, 360도 회전 캐스터, PC 하드쉘, 10kg.', imageUrl: 'https://images.unsplash.com/photo-1565026057447-bc90a3dceb87?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 7, name: '아이폰15 강화유리 케이스', price: 15000, stockQuantity: 87, categoryName: '전자기기', category: '전자기기', description: '군사규격 충격 흡수, 황변 방지 투명 소재, 맥세이프 호환.', imageUrl: 'https://images.unsplash.com/photo-1601784551446-20c9e07cdbdb?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 8, name: '유기농 그린티 50봉', price: 22000, stockQuantity: 53, categoryName: '식품', category: '식품', description: '제주 유기농 녹차잎 100%, 카페인 낮은 어린 순 채취, 개별 포장.', imageUrl: 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 9, name: '스탠딩 책상 전동 높이조절', price: 320000, stockQuantity: 7, categoryName: '홈/인테리어', category: '홈/인테리어', description: '70-120cm 무단계 조절, 메모리 기능 4단계, 최대 80kg 지지.', imageUrl: 'https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 10, name: '프로틴 쉐이크 바닐라 1kg', price: 38000, stockQuantity: 44, categoryName: '건강', category: '건강', description: 'WPI 90% 농축유청단백질, 1회 25g 단백질, 저당·저지방.', imageUrl: 'https://images.unsplash.com/photo-1593095948071-474c5cc2989d?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 11, name: '4K 웹캠 오토포커스', price: 67000, stockQuantity: 23, categoryName: '전자기기', category: '전자기기', description: '소니 센서, AI 배경 블러, 스테레오 노이즈 캔슬링 마이크.', imageUrl: 'https://images.unsplash.com/photo-1587826080692-f439cd0b70da?w=400&h=400&fit=crop', sellerId: 1 },
  { id: 12, name: '제주 감귤 3kg 선물세트', price: 29000, stockQuantity: 38, categoryName: '식품', category: '식품', description: '당도 13브릭스 이상 엄선, 친환경 재배, 산지 직송.', imageUrl: 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=400&h=400&fit=crop', sellerId: 1 },
];

const DEMO_USER = {
  id: 1,
  email: 'demo@livemart.com',
  name: '데모 관리자',
  phoneNumber: '010-1234-5678',
  role: 'ADMIN',
  profileImageUrl: null,
  address: '서울시 강남구 테헤란로 123 LiveMart빌딩',
  createdAt: '2024-01-15T09:00:00',
  point: 5000,
};

const DEMO_ORDERS = [
  {
    id: 1,
    orderNumber: 'ORD-20240315-001',
    userId: 1,
    status: 'DELIVERED',
    totalAmount: 89000,
    deliveryAddress: '서울시 강남구 테헤란로 123',
    phoneNumber: '010-1234-5678',
    orderNote: '',
    paymentMethod: 'CARD',
    createdAt: '2024-03-15T10:00:00',
    items: [{ productId: 1, productName: '무선 블루투스 이어폰 ANC Pro', quantity: 1, price: 89000, productPrice: 89000, totalPrice: 89000 }],
  },
  {
    id: 2,
    orderNumber: 'ORD-20240320-002',
    userId: 1,
    status: 'SHIPPED',
    totalAmount: 53500,
    deliveryAddress: '서울시 강남구 테헤란로 123',
    phoneNumber: '010-1234-5678',
    orderNote: '',
    paymentMethod: 'CARD',
    createdAt: '2024-03-20T14:30:00',
    items: [
      { productId: 2, productName: '프리미엄 요가 매트 8mm', quantity: 1, price: 35000, productPrice: 35000, totalPrice: 35000 },
      { productId: 3, productName: '한방 갈비탕 밀키트 2인분', quantity: 1, price: 18500, productPrice: 18500, totalPrice: 18500 },
    ],
  },
  {
    id: 3,
    orderNumber: 'ORD-20240325-003',
    userId: 1,
    status: 'PENDING',
    totalAmount: 45000,
    deliveryAddress: '서울시 강남구 테헤란로 123',
    phoneNumber: '010-1234-5678',
    orderNote: '빠른 배송 부탁드립니다',
    paymentMethod: 'CARD',
    createdAt: '2024-03-25T09:15:00',
    items: [{ productId: 4, productName: '스마트 체중계 블루투스', quantity: 1, price: 45000, productPrice: 45000, totalPrice: 45000 }],
  },
];

const DEMO_REVIEWS = [
  { id: 1, userId: 1, userName: '김*현', rating: 5, title: '정말 좋아요!', content: '음질이 뛰어나고 노이즈 캔슬링이 탁월합니다. 강력 추천!', createdAt: '2024-02-10T09:00:00', helpful: 12 },
  { id: 2, userId: 2, userName: '박*영', rating: 4, title: '가성비 최고', content: '이 가격에 이 품질이면 충분합니다. 배터리도 오래가요.', createdAt: '2024-02-15T14:00:00', helpful: 8 },
  { id: 3, userId: 3, userName: '이*수', rating: 5, title: '재구매 의사 있음', content: '처음에 반신반의했는데 쓰고 나서 대만족입니다.', createdAt: '2024-03-01T11:30:00', helpful: 5 },
];

const DEMO_COUPONS = [
  { id: 1, code: 'WELCOME10', name: '신규 가입 할인', discountType: 'PERCENTAGE', discountValue: 10, minimumOrderAmount: 30000, expiresAt: '2025-12-31T23:59:59', isActive: true },
  { id: 2, code: 'SPRING5000', name: '봄 시즌 할인', discountType: 'FIXED', discountValue: 5000, minimumOrderAmount: 50000, expiresAt: '2025-06-30T23:59:59', isActive: true },
  { id: 3, code: 'TECH15', name: '전자기기 15% 할인', discountType: 'PERCENTAGE', discountValue: 15, minimumOrderAmount: 60000, expiresAt: '2025-09-30T23:59:59', isActive: true },
];

// ─────────────────────────────────────────────────────────────────
// 유틸
// ─────────────────────────────────────────────────────────────────

function makeDemoJwt(role: string): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  const payload = Buffer.from(JSON.stringify({
    sub: '1',
    email: 'demo@livemart.com',
    role,
    exp: 9999999999,
  })).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  return `${header}.${payload}.demo-signature`;
}

function paginate<T>(items: T[], page: number, size: number) {
  const start = page * size;
  const content = items.slice(start, start + size);
  return {
    content,
    totalElements: items.length,
    totalPages: size > 0 ? Math.ceil(items.length / size) : 0,
    number: page,
    size,
    _demo: true,
  };
}

function ok(data: unknown, status = 200) {
  return NextResponse.json(data, { status });
}

function health() {
  return ok({ status: 'UP', _demo: true });
}

// ─────────────────────────────────────────────────────────────────
// GET
// ─────────────────────────────────────────────────────────────────

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const seg = path; // e.g. ['products', '1', 'reviews']
  const proxied = await proxyRequest(request, 'GET', seg);
  if (proxied) return proxied;
  const sp = request.nextUrl.searchParams;

  // ── health endpoints ──────────────────────────────────────────
  if (seg.at(-1) === 'health') return health();

  // ── products ──────────────────────────────────────────────────
  if (seg[0] === 'products') {
    // /api/products/search/autocomplete
    if (seg[1] === 'search' && seg[2] === 'autocomplete') {
      const prefix = sp.get('prefix')?.toLowerCase() || '';
      const suggestions = DEMO_PRODUCTS
        .filter(p => p.name.toLowerCase().includes(prefix))
        .slice(0, 5)
        .map(p => p.name);
      return ok(suggestions);
    }

    // /api/products/search
    if (seg[1] === 'search') {
      const kw = (sp.get('keyword') || '').toLowerCase();
      const filtered = DEMO_PRODUCTS.filter(p =>
        p.name.toLowerCase().includes(kw) || p.description.toLowerCase().includes(kw)
      );
      return ok(paginate(filtered, 0, filtered.length));
    }

    // /api/products/{id}/reviews/summary
    if (seg[2] === 'reviews' && seg[3] === 'summary') {
      return ok({
        productId: parseInt(seg[1]),
        totalCount: DEMO_REVIEWS.length,
        averageRating: 4.7,
        ratingDistribution: { 5: 2, 4: 1, 3: 0, 2: 0, 1: 0 },
        _demo: true,
      });
    }

    // /api/products/{id}/reviews
    if (seg[2] === 'reviews') {
      const page = parseInt(sp.get('page') || '0');
      const size = parseInt(sp.get('size') || '10');
      return ok(paginate(DEMO_REVIEWS, page, size));
    }

    // /api/products/{id}
    if (seg[1] && !isNaN(parseInt(seg[1]))) {
      const id = parseInt(seg[1]);
      const product = DEMO_PRODUCTS.find(p => p.id === id) ?? DEMO_PRODUCTS[0];
      return ok({ ...product, _demo: true });
    }

    // /api/products (list)
    const page = parseInt(sp.get('page') || '0');
    const size = parseInt(sp.get('size') || '12');
    const search = (sp.get('search') || '').toLowerCase();
    const category = sp.get('category') || '';
    let filtered = DEMO_PRODUCTS;
    if (search) filtered = filtered.filter(p => p.name.toLowerCase().includes(search) || p.description.toLowerCase().includes(search));
    if (category) filtered = filtered.filter(p => p.categoryName === category);
    return ok(paginate(filtered, page, size));
  }

  // ── users ─────────────────────────────────────────────────────
  if (seg[0] === 'users') {
    // /api/users/me
    if (seg[1] === 'me') {
      return ok({ ...DEMO_USER, _demo: true });
    }

    // /api/users/count (admin)
    if (seg[1] === 'count') {
      return ok({ count: 1284, _demo: true });
    }

    // /api/users/{id}/cart
    if (seg[2] === 'cart' && !seg[3]) {
      return ok({
        userId: parseInt(seg[1]),
        items: [
          { productId: 1, productName: '무선 블루투스 이어폰 ANC Pro', price: 89000, quantity: 1, imageUrl: DEMO_PRODUCTS[0].imageUrl },
          { productId: 7, productName: '아이폰15 강화유리 케이스', price: 15000, quantity: 2, imageUrl: DEMO_PRODUCTS[6].imageUrl },
        ],
        totalAmount: 119000,
        _demo: true,
      });
    }

    // /api/users/{id}/wishlist
    if (seg[2] === 'wishlist' && !seg[3]) {
      return ok({
        content: [
          { id: 1, productId: 1, productName: '무선 블루투스 이어폰 ANC Pro', productPrice: 89000, productImageUrl: DEMO_PRODUCTS[0].imageUrl },
          { id: 2, productId: 5, productName: '럭셔리 코튼 침구 세트 퀸', productPrice: 128000, productImageUrl: DEMO_PRODUCTS[4].imageUrl },
          { id: 3, productId: 9, productName: '스탠딩 책상 전동 높이조절', productPrice: 320000, productImageUrl: DEMO_PRODUCTS[8].imageUrl },
        ],
        totalElements: 3,
        _demo: true,
      });
    }

    // /api/users/{id}/addresses
    if (seg[2] === 'addresses') {
      return ok([
        { id: 1, alias: '집', address: '서울시 강남구 테헤란로 123', detail: 'LiveMart빌딩 456호', isDefault: true },
      ]);
    }

    // /api/users/{id}
    if (seg[1] && !isNaN(parseInt(seg[1])) && !seg[2]) {
      return ok({ ...DEMO_USER, id: parseInt(seg[1]), _demo: true });
    }

    // /api/users (admin list)
    const page = parseInt(sp.get('page') || '0');
    const size = parseInt(sp.get('size') || '10');
    const demoUsers = Array.from({ length: 10 }, (_, i) => ({
      id: i + 1,
      email: `user${i + 1}@livemart.com`,
      name: `사용자 ${i + 1}`,
      role: i === 0 ? 'ADMIN' : 'BUYER',
      createdAt: '2024-01-01T00:00:00',
    }));
    return ok(paginate(demoUsers, page, size));
  }

  // ── orders ────────────────────────────────────────────────────
  if (seg[0] === 'orders') {
    // /api/orders/user/{userId}
    if (seg[1] === 'user') {
      const page = parseInt(sp.get('page') || '0');
      const size = parseInt(sp.get('size') || '10');
      return ok(paginate(DEMO_ORDERS, page, size));
    }

    // /api/orders/number/{orderNumber}
    if (seg[1] === 'number') {
      const order = DEMO_ORDERS.find(o => o.orderNumber === seg[2]) ?? DEMO_ORDERS[0];
      return ok({ ...order, _demo: true });
    }

    // /api/orders/query/statistics
    if (seg[1] === 'query' && seg[2] === 'statistics') {
      return ok({ totalOrders: 3, totalAmount: 187500, _demo: true });
    }

    // /api/orders/{id}
    if (seg[1] && !isNaN(parseInt(seg[1]))) {
      const id = parseInt(seg[1]);
      const order = DEMO_ORDERS.find(o => o.id === id) ?? DEMO_ORDERS[0];
      return ok({ ...order, _demo: true });
    }

    // /api/orders (admin list)
    const page = parseInt(sp.get('page') || '0');
    const size = parseInt(sp.get('size') || '10');
    return ok(paginate(DEMO_ORDERS, page, size));
  }

  // ── payments ──────────────────────────────────────────────────
  if (seg[0] === 'payments') {
    // /api/payments/order/{orderNumber}
    if (seg[1] === 'order') {
      return ok({
        id: 1,
        orderNumber: seg[2],
        amount: 89000,
        method: 'CARD',
        status: 'SUCCESS',
        transactionId: 'demo-txn-001',
        createdAt: '2024-03-15T10:05:00',
        _demo: true,
      });
    }
  }

  // ── coupons ───────────────────────────────────────────────────
  if (seg[0] === 'coupons') {
    if (seg[1]) {
      const coupon = DEMO_COUPONS.find(c => c.code === seg[1]) ?? DEMO_COUPONS[0];
      // /api/coupons/{code}/preview
      if (seg[2] === 'preview') {
        const orderAmount = parseInt(sp.get('orderAmount') || '0');
        const discount = coupon.discountType === 'PERCENTAGE'
          ? Math.floor(orderAmount * coupon.discountValue / 100)
          : coupon.discountValue;
        return ok({ discount, finalAmount: orderAmount - discount, _demo: true });
      }
      return ok({ ...coupon, _demo: true });
    }
    const page = parseInt(sp.get('page') || '0');
    const size = parseInt(sp.get('size') || '10');
    return ok(paginate(DEMO_COUPONS, page, size));
  }

  // ── inventory ─────────────────────────────────────────────────
  if (seg[0] === 'inventory' && seg[1] === 'product') {
    const productId = parseInt(seg[2] || '1');
    const product = DEMO_PRODUCTS.find(p => p.id === productId);
    return ok({
      productId,
      availableQuantity: product?.stockQuantity ?? 50,
      status: (product?.stockQuantity ?? 50) > 0 ? 'IN_STOCK' : 'OUT_OF_STOCK',
      _demo: true,
    });
  }

  // ── recommendations ───────────────────────────────────────────
  if (seg[0] === 'recommendations' && seg[1] === 'user') {
    return ok(DEMO_PRODUCTS.slice(0, 4).map(p => ({ ...p, _demo: true })));
  }

  // ── delivery ──────────────────────────────────────────────────
  if (seg[0] === 'delivery') {
    return ok({
      trackingNumber: seg[1] || 'DEMO-TRACK-001',
      courierCompany: 'CJ대한통운',
      status: 'IN_TRANSIT',
      currentLocation: '서울 강남 배송센터',
      estimatedDelivery: '2024-03-27',
      history: [
        { status: 'PICKED_UP', location: '제주 출발지', timestamp: '2024-03-25T09:00:00' },
        { status: 'IN_TRANSIT', location: '서울 강남 배송센터', timestamp: '2024-03-26T14:00:00' },
      ],
      _demo: true,
    });
  }

  // ── returns ───────────────────────────────────────────────────
  if (seg[0] === 'returns') {
    if (seg[1] === 'user') {
      return ok(paginate([], 0, 10));
    }
    if (seg[1] && !isNaN(parseInt(seg[1]))) {
      return ok({ id: parseInt(seg[1]), status: 'PENDING', reason: '데모 반품', _demo: true });
    }
  }

  // ── analytics ─────────────────────────────────────────────────
  if (seg[0] === 'analytics') {
    if (seg.includes('stream')) {
      const body = new ReadableStream({
        start(controller) {
          const data = JSON.stringify({ activeUsers: 47, dailyOrders: 324, revenue: 18750000, topProducts: DEMO_PRODUCTS.slice(0, 3).map(p => ({ name: p.name, sales: 28 })), _demo: true });
          controller.enqueue(new TextEncoder().encode(`data: ${data}\n\n`));
          controller.close();
        },
      });
      return new Response(body, {
        headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
      });
    }
    return ok({ activeUsers: 47, dailyOrders: 324, revenue: 18750000, conversionRate: 3.2, topProducts: DEMO_PRODUCTS.slice(0, 3).map(p => ({ name: p.name, sales: 28 })), recentOrders: DEMO_ORDERS, _demo: true });
  }

  // ── dashboard (LiveActivityBar 전용: activeUsers, dailyOrders, revenue) ──────
  if (seg[0] === 'dashboard') {
    if (seg.includes('stream')) {
      const body = new ReadableStream({
        start(controller) {
          const data = JSON.stringify({ activeUsers: 47, dailyOrders: 324, revenue: 18750000, topProducts: DEMO_PRODUCTS.slice(0, 3).map(p => ({ name: p.name, sales: 28 })), _demo: true });
          controller.enqueue(new TextEncoder().encode(`data: ${data}\n\n`));
          controller.close();
        },
      });
      return new Response(body, {
        headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
      });
    }
    return ok({ activeUsers: 47, dailyOrders: 324, revenue: 18750000, topProducts: DEMO_PRODUCTS.slice(0, 3).map(p => ({ name: p.name, sales: 28 })), _demo: true });
  }

  // ── sellers ───────────────────────────────────────────────────
  if (seg[0] === 'sellers') {
    // /api/sellers/{id}/dashboard
    if (seg[2] === 'dashboard') {
      return ok({
        totalRevenue: 12850000,
        totalOrders: 87,
        totalProducts: 12,
        pendingOrders: 5,
        recentOrders: DEMO_ORDERS,
        lowStockProducts: DEMO_PRODUCTS.filter(p => p.stockQuantity <= 10).map(p => ({ id: p.id, name: p.name, stockQuantity: p.stockQuantity })),
        _demo: true,
      });
    }
    // /api/sellers/{id}/products
    if (seg[2] === 'products') {
      return ok(paginate(DEMO_PRODUCTS, 0, 50));
    }
    return ok({ message: '판매자 신청이 접수되었습니다. (데모)', _demo: true });
  }

  // ── notifications ─────────────────────────────────────────────
  if (seg[0] === 'notifications') {
    // /api/notifications/stream/{uid} — SSE
    if (seg.includes('stream')) {
      const enc = new TextEncoder();
      const body = new ReadableStream({
        start(controller) {
          // retry 60s to reduce reconnect frequency in demo
          controller.enqueue(enc.encode('retry: 60000\n'));
          const notif = JSON.stringify({ id: 1, type: 'ORDER_STATUS', title: '주문 배송 시작', message: '주문 #ORD-20240320-002 이(가) 배송을 시작했습니다.', read: false, createdAt: new Date().toISOString() });
          controller.enqueue(enc.encode(`data: ${notif}\n\n`));
          // close after 20s to avoid serverless timeout
          setTimeout(() => { try { controller.close(); } catch {} }, 20000);
        },
      });
      return new Response(body, {
        headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no' },
      });
    }
    // /api/notifications/user/{uid}
    if (seg[1] === 'user') {
      return ok({
        content: [
          { id: 1, type: 'ORDER_STATUS', title: '주문 배송 시작', message: '주문 #ORD-20240320-002 이(가) 배송을 시작했습니다.', read: false, createdAt: new Date(Date.now() - 3600000).toISOString() },
          { id: 2, type: 'PROMOTION', title: '봄 시즌 세일 시작!', message: '전 카테고리 최대 30% 할인. 오늘만 특가!', read: true, createdAt: new Date(Date.now() - 86400000).toISOString() },
        ],
        totalElements: 2,
        _demo: true,
      });
    }
    return ok({ content: [], totalElements: 0, _demo: true });
  }

  // ── v1 (MFA 등) ───────────────────────────────────────────────
  if (seg[0] === 'v1') {
    return ok({ enabled: false, _demo: true });
  }

  // ── ai ────────────────────────────────────────────────────────
  if (seg[0] === 'ai') {
    if (seg[1] === 'recommend') {
      return ok({
        recommendations: DEMO_PRODUCTS.slice(0, 4).map(p => ({
          productId: p.id, productName: p.name,
          category: p.categoryName,
          reason: '구매 이력 기반 추천 상품입니다.',
          imageUrl: p.imageUrl, price: p.price,
        })),
        reasoning: '최근 조회 및 구매 패턴 분석 기반',
        cached: false, _demo: true,
      });
    }
    if (seg[1] === 'chat' || seg[1] === 'chat' && seg[2] === 'stream') {
      return ok({ message: 'AI 채팅은 백엔드 연동 환경에서 사용 가능합니다. (데모)', _demo: true });
    }
    return ok({ message: '데모 모드에서는 AI 기능이 제한됩니다.', _demo: true });
  }

  // ── sellers ───────────────────────────────────────────────────
  if (seg[0] === 'sellers') {
    return ok({ message: '판매자 신청이 접수되었습니다. (데모)', _demo: true });
  }

  // ── web-vitals ────────────────────────────────────────────────
  if (seg[0] === 'analytics' && seg[1] === 'web-vitals') {
    return ok({ ok: true });
  }

  // fallback
  return ok({ _demo: true, message: 'Demo mode — backend not connected' });
}

// ─────────────────────────────────────────────────────────────────
// POST
// ─────────────────────────────────────────────────────────────────

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const seg = path;
  const proxied = await proxyRequest(request, 'POST', seg);
  if (proxied) return proxied;

  // ── users/login ───────────────────────────────────────────────
  if (seg[0] === 'users' && seg[1] === 'login') {
    const token = makeDemoJwt('ADMIN');
    const response = NextResponse.json({
      accessToken: token,
      refreshToken: `demo-refresh-${Date.now()}`,
      userId: 1,
      name: '데모 관리자',
      role: 'ADMIN',
      _demo: true,
    });
    response.cookies.set('access_token', token, {
      httpOnly: true,
      secure: true,
      sameSite: 'lax',
      maxAge: 60 * 60 * 24 * 30,
      path: '/',
    });
    return response;
  }

  // ── users/signup ──────────────────────────────────────────────
  if (seg[0] === 'users' && seg[1] === 'signup') {
    return ok({ id: 2, message: '회원가입이 완료되었습니다. (데모)', _demo: true }, 201);
  }

  // ── users/logout ──────────────────────────────────────────────
  if (seg[0] === 'users' && seg[1] === 'logout') {
    const response = NextResponse.json({ message: '로그아웃되었습니다.', _demo: true });
    response.cookies.delete('access_token');
    return response;
  }

  // ── users/refresh ─────────────────────────────────────────────
  if (seg[0] === 'users' && seg[1] === 'refresh') {
    const token = makeDemoJwt('ADMIN');
    const response = NextResponse.json({ accessToken: token, _demo: true });
    response.cookies.set('access_token', token, {
      httpOnly: true, secure: true, sameSite: 'lax',
      maxAge: 60 * 60 * 24 * 30, path: '/',
    });
    return response;
  }

  // ── users/email/send & verify (회원가입 이메일 인증) ─────────
  if (seg[0] === 'users' && seg[1] === 'email') {
    if (seg[2] === 'send') {
      return ok({ message: '인증 코드를 발송했습니다. 데모 코드: 123456', sent: true, _demo: true });
    }
    if (seg[2] === 'verify') {
      return ok({ verified: true, message: '인증 완료 (데모)', _demo: true });
    }
  }

  // ── users/{id}/cart ───────────────────────────────────────────
  if (seg[0] === 'users' && seg[2] === 'cart') {
    return ok({ message: '장바구니에 추가되었습니다. (데모)', _demo: true }, 201);
  }

  // ── users/{id}/wishlist ───────────────────────────────────────
  if (seg[0] === 'users' && seg[2] === 'wishlist') {
    return ok({ message: '위시리스트에 추가되었습니다. (데모)', _demo: true }, 201);
  }

  // ── notifications read ────────────────────────────────────────
  if (seg[0] === 'notifications' || (seg[0] === 'api' && seg[1] === 'notifications')) {
    return ok({ message: '읽음 처리되었습니다.', _demo: true });
  }

  // ── orders ────────────────────────────────────────────────────
  if (seg[0] === 'orders' && !seg[1]) {
    const body = await request.json().catch(() => ({}));
    const newOrder = {
      id: 99,
      orderNumber: `ORD-DEMO-${Date.now()}`,
      userId: body.userId ?? 1,
      status: 'PENDING',
      totalAmount: (body.items ?? []).reduce((sum: number, item: { productId: number; quantity: number }) => {
        const p = DEMO_PRODUCTS.find(p => p.id === item.productId);
        return sum + (p?.price ?? 0) * item.quantity;
      }, 0),
      items: body.items ?? [],
      deliveryAddress: body.deliveryAddress ?? '',
      phoneNumber: body.phoneNumber ?? '',
      paymentMethod: body.paymentMethod ?? 'CARD',
      createdAt: new Date().toISOString(),
      _demo: true,
    };
    return ok(newOrder, 201);
  }

  // ── orders/{id}/cancel,confirm,ship,deliver ───────────────────
  if (seg[0] === 'orders' && seg[1] && seg[2]) {
    const action = seg[2];
    const statusMap: Record<string, string> = {
      cancel: 'CANCELLED',
      confirm: 'CONFIRMED',
      ship: 'SHIPPING',
      deliver: 'DELIVERED',
    };
    return ok({ id: parseInt(seg[1]), status: statusMap[action] ?? 'UPDATED', _demo: true });
  }

  // ── payments ──────────────────────────────────────────────────
  if (seg[0] === 'payments' && !seg[1]) {
    return ok({
      transactionId: `demo-txn-${Date.now()}`,
      status: 'SUCCESS',
      amount: 0,
      _demo: true,
    }, 201);
  }

  if (seg[0] === 'payments' && seg[1] === 'refund') {
    return ok({ message: '환불 처리되었습니다. (데모)', _demo: true });
  }

  // ── payments/toss/confirm ─────────────────────────────────────
  if (seg[0] === 'payments' && seg[1] === 'toss' && seg[2] === 'confirm') {
    return ok({ status: 'DONE', message: '결제가 완료되었습니다. (데모)', _demo: true });
  }

  // ── returns ───────────────────────────────────────────────────
  if (seg[0] === 'returns') {
    return ok({ id: 1, status: 'PENDING', message: '반품 신청이 접수되었습니다. (데모)', _demo: true }, 201);
  }

  // ── delivery ──────────────────────────────────────────────────
  if (seg[0] === 'delivery') {
    return ok({ trackingNumber: `DEMO-${Date.now()}`, status: 'CREATED', _demo: true }, 201);
  }

  // ── sellers/apply ─────────────────────────────────────────────
  if (seg[0] === 'sellers' && seg[1] === 'apply') {
    return ok({ message: '판매자 신청이 완료되었습니다. (데모)', _demo: true }, 201);
  }

  // ── coupons (admin create) ────────────────────────────────────
  if (seg[0] === 'coupons' && !seg[1]) {
    return ok({ id: Math.floor(Math.random() * 900) + 100, message: '쿠폰이 생성되었습니다. (데모)', _demo: true }, 201);
  }

  // ── products/{id}/reviews ─────────────────────────────────────
  if (seg[0] === 'products' && seg[2] === 'reviews' && !seg[3]) {
    return ok({ id: 99, message: '리뷰가 등록되었습니다. (데모)', _demo: true }, 201);
  }

  // ── products/{id}/reviews/{reviewId}/helpful ─────────────────
  if (seg[0] === 'products' && seg[2] === 'reviews' && seg[4] === 'helpful') {
    return ok({ message: '도움됨 처리되었습니다. (데모)', _demo: true });
  }

  // ── ai/chat ───────────────────────────────────────────────────
  if (seg[0] === 'ai' && (seg[1] === 'chat' || seg[1] === 'description' || seg[1] === 'recommend')) {
    return ok({ message: '데모 모드에서는 AI 기능이 제한됩니다. 로컬 환경에서 실행해주세요.', _demo: true });
  }

  // ── analytics/web-vitals ──────────────────────────────────────
  if (seg[0] === 'analytics' && seg[1] === 'web-vitals') {
    return ok({ ok: true });
  }

  // fallback
  return ok({ _demo: true, message: 'Demo mode — backend not connected' }, 200);
}

// ─────────────────────────────────────────────────────────────────
// PUT
// ─────────────────────────────────────────────────────────────────

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const seg = path;

  // /api/products/{id}/stock
  if (seg[0] === 'products' && seg[2] === 'stock') {
    const sp = request.nextUrl.searchParams;
    const qty = parseInt(sp.get('stockQuantity') || '0');
    return ok({ productId: parseInt(seg[1]), stockQuantity: qty, _demo: true });
  }

  // /api/users/{id}/cart/{productId}
  if (seg[0] === 'users' && seg[2] === 'cart' && seg[3]) {
    return ok({ message: '수량이 수정되었습니다. (데모)', _demo: true });
  }

  // /api/coupons/{id}/toggle
  if (seg[0] === 'coupons' && seg[2] === 'toggle') {
    return ok({ message: '쿠폰 상태가 변경되었습니다. (데모)', _demo: true });
  }

  // /api/notifications/user/{id}/read-all
  if (seg[0] === 'notifications' && seg[1] === 'user') {
    return ok({ message: '읽음 처리되었습니다.', _demo: true });
  }

  return ok({ _demo: true });
}

// ─────────────────────────────────────────────────────────────────
// DELETE
// ─────────────────────────────────────────────────────────────────

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const seg = path;
  const proxied = await proxyRequest(request, 'DELETE', seg);
  if (proxied) return proxied;

  // /api/users/{id}/cart (clear)
  if (seg[0] === 'users' && seg[2] === 'cart' && !seg[3]) {
    return ok({ message: '장바구니가 비워졌습니다. (데모)', _demo: true });
  }

  // /api/users/{id}/cart/{productId}
  if (seg[0] === 'users' && seg[2] === 'cart' && seg[3]) {
    return ok({ message: '상품이 삭제되었습니다. (데모)', _demo: true });
  }

  // /api/users/{id}/wishlist/{productId}
  if (seg[0] === 'users' && seg[2] === 'wishlist' && seg[3]) {
    return ok({ message: '위시리스트에서 제거되었습니다. (데모)', _demo: true });
  }

  // /api/products/{id}/reviews/{reviewId}
  if (seg[0] === 'products' && seg[2] === 'reviews' && seg[3]) {
    return ok({ message: '리뷰가 삭제되었습니다. (데모)', _demo: true });
  }

  return ok({ _demo: true });
}

// ─────────────────────────────────────────────────────────────────
// PATCH
// ─────────────────────────────────────────────────────────────────

export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const seg = path;
  const proxied = await proxyRequest(request, 'PATCH', seg);
  if (proxied) return proxied;

  // /api/users/me (프로필 수정)
  if (seg[0] === 'users' && seg[1] === 'me') {
    return ok({ message: '프로필이 수정되었습니다. (데모)', _demo: true });
  }

  return ok({ _demo: true });
}
