'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { productApi, reviewApi } from '@/api/productApi';
import { useCartStore } from '@/store/cartStore';
import { GlobalNav } from '@/components/GlobalNav';
import { CartSummary } from '@/components/CartSummary';
import { ProductCard } from '@/components/ProductCard';
import toast from 'react-hot-toast';

interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stockQuantity: number;
  categoryId: number;
  categoryName: string;
  sellerId: number;
  imageUrl?: string;
  createdAt: string;
}
interface Review {
  id: number;
  userId: number;
  userName: string;
  rating: number;
  title: string;
  content: string;
  helpfulCount: number;
  createdAt: string;
}
interface ReviewSummary {
  averageRating: number;
  totalCount: number;
  ratingDistribution: Record<number, number>;
}

function Stars({ rating, size = 'sm', interactive = false, onRate }: {
  rating: number; size?: 'sm'|'md'|'lg'; interactive?: boolean; onRate?: (r: number)=>void;
}) {
  const s = { sm:'w-4 h-4', md:'w-5 h-5', lg:'w-6 h-6' }[size];
  return (
    <div className="flex gap-0.5">
      {[1,2,3,4,5].map(star => (
        <button key={star} type="button" disabled={!interactive} onClick={()=>onRate?.(star)}
          className={`${s} ${interactive?'cursor-pointer hover:scale-125 transition':'cursor-default'} ${star<=rating?'text-yellow-400':'text-gray-200'}`}>
          <svg fill="currentColor" viewBox="0 0 20 20"><path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/></svg>
        </button>
      ))}
    </div>
  );
}

// Generate gallery images from product imageUrl + picsum variants
function getGalleryImages(imageUrl: string | undefined, productId: number): string[] {
  const images: string[] = [];
  if (imageUrl) images.push(imageUrl);
  // Add 3 picsum variant images
  [productId * 7 + 1, productId * 11 + 3, productId * 13 + 7].forEach(seed => {
    images.push(`https://picsum.photos/seed/${seed}/600/600`);
  });
  return images;
}

// Save to recently viewed
function saveRecentlyViewed(product: Product) {
  try {
    const stored = localStorage.getItem('recentlyViewed');
    const list: Pick<Product, 'id' | 'name' | 'price' | 'imageUrl'>[] = stored ? JSON.parse(stored) : [];
    const filtered = list.filter(p => p.id !== product.id);
    const entry = { id: product.id, name: product.name, price: product.price, imageUrl: product.imageUrl };
    filtered.unshift(entry);
    localStorage.setItem('recentlyViewed', JSON.stringify(filtered.slice(0, 20)));
  } catch {}
}

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const productId = Number(params.id);
  const addItem = useCartStore((s) => s.addItem);

  const [product, setProduct] = useState<Product | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [summary, setSummary] = useState<ReviewSummary | null>(null);
  const [relatedProducts, setRelatedProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [activeTab, setActiveTab] = useState<'description'|'reviews'>('description');
  const [showReviewForm, setShowReviewForm] = useState(false);
  const [reviewForm, setReviewForm] = useState({ rating: 5, title: '', content: '' });
  const [submitting, setSubmitting] = useState(false);
  const [wished, setWished] = useState(false);
  const [selectedImageIdx, setSelectedImageIdx] = useState(0);
  const [galleryImages, setGalleryImages] = useState<string[]>([]);

  const discountRate = [0,0,5,10,10,15,15,20,20,25,30,0,10,15][productId % 14];

  useEffect(() => {
    Promise.all([
      productApi.getProduct(productId).then(p => {
        setProduct(p);
        const imgs = getGalleryImages(p.imageUrl, productId);
        setGalleryImages(imgs);
        saveRecentlyViewed(p);
        // Load related products by category
        return productApi.getProducts({ page: 0, size: 12 }).then(data => {
          const related = (data.content || []).filter((r: Product) => r.id !== productId && r.categoryId === p.categoryId).slice(0, 8);
          setRelatedProducts(related.length > 0 ? related : (data.content || []).filter((r: Product) => r.id !== productId).slice(0, 8));
        }).catch(() => null);
      }).catch(()=>null),
      reviewApi.getReviews(productId).then(d=>setReviews(d.content||[])).catch(()=>null),
      reviewApi.getReviewSummary(productId).then(setSummary).catch(()=>null),
    ]).finally(()=>setLoading(false));
  }, [productId]);

  const handleAddToCart = () => {
    if (!product) return;
    addItem({ productId: product.id, name: product.name, price: product.price, quantity, imageUrl: product.imageUrl });
    toast.success(`장바구니에 ${quantity}개 추가됐습니다!`, { icon: '🛒' });
  };

  const handleSubmitReview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewForm.content.trim()) { toast.error('리뷰 내용을 입력해주세요.'); return; }
    setSubmitting(true);
    try {
      await reviewApi.createReview(productId, { userId: parseInt(localStorage.getItem('userId')||'1'), ...reviewForm });
      toast.success('리뷰가 등록됐습니다!');
      setShowReviewForm(false);
      setReviewForm({ rating:5, title:'', content:'' });
      reviewApi.getReviews(productId).then(d=>setReviews(d.content||[]));
      reviewApi.getReviewSummary(productId).then(setSummary).catch(()=>null);
    } catch { toast.error('리뷰 등록에 실패했습니다.'); }
    finally { setSubmitting(false); }
  };

  if (loading) return (
    <div className="min-h-screen bg-gray-100"><GlobalNav />
      <div className="max-w-[1280px] mx-auto px-4 py-10 grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="aspect-square bg-gray-200 rounded-2xl animate-pulse"/>
        <div className="space-y-4 pt-4">{[80,60,40,50,40,90].map((w,i)=><div key={i} className="h-5 bg-gray-200 rounded animate-pulse" style={{width:`${w}%`}}/>)}</div>
      </div>
    </div>
  );

  if (!product) return (
    <div className="min-h-screen bg-gray-100"><GlobalNav />
      <div className="flex items-center justify-center pt-20">
        <div className="text-center card p-12">
          <div className="text-5xl mb-4">😢</div>
          <h2 className="text-xl font-bold text-gray-700 mb-4">상품을 찾을 수 없습니다</h2>
          <button onClick={()=>router.push('/products')} className="btn-primary">상품 목록으로</button>
        </div>
      </div>
    </div>
  );

  const inStock = product.stockQuantity > 0;
  const isFreeShipping = product.price >= 50000;
  const originalPrice = discountRate > 0 ? Math.round(product.price / (1 - discountRate/100) / 100) * 100 : product.price;
  const displayRating = summary?.averageRating || 4.5;
  const reviewCount = summary?.totalCount || 0;

  return (
    <main className="min-h-screen bg-gray-100 pb-14 md:pb-0">
      <GlobalNav />
      <div className="max-w-[1280px] mx-auto px-4 py-5">
        {/* 브레드크럼 */}
        <nav className="flex items-center gap-2 text-sm text-gray-500 mb-5">
          {[['홈','/'], ['상품','/products'], ...(product.categoryName ? [[product.categoryName,'#']] : [])].map(([label, href], i, arr) => (
            <span key={label} className="flex items-center gap-2">
              {i > 0 && <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7"/></svg>}
              <a href={href} className="hover:text-red-600 transition-colors">{label}</a>
            </span>
          ))}
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7"/></svg>
          <span className="text-gray-700 font-medium line-clamp-1">{product.name}</span>
        </nav>

        {/* 상품 메인 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* 이미지 갤러리 */}
          <div className="flex flex-col gap-3">
            <div className="bg-white rounded-2xl overflow-hidden aspect-square shadow-sm border border-gray-100 relative group">
              {galleryImages.length > 0 ? (
                <img src={galleryImages[selectedImageIdx]} alt={product.name}
                  className="w-full h-full object-cover transition-all duration-300"
                  onError={(e) => {
                    const t = e.target as HTMLImageElement;
                    if (selectedImageIdx === 0 && product.imageUrl) {
                      // skip
                    } else {
                      t.style.display = 'none';
                    }
                  }}/>
              ) : (
                <div className="w-full h-full flex items-center justify-center text-9xl bg-gradient-to-br from-gray-50 to-gray-100">📦</div>
              )}
              {/* 좌우 화살표 */}
              {galleryImages.length > 1 && (
                <>
                  <button onClick={() => setSelectedImageIdx(i => (i - 1 + galleryImages.length) % galleryImages.length)}
                    className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/80 hover:bg-white rounded-full shadow-md flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity text-gray-700 text-xl">‹</button>
                  <button onClick={() => setSelectedImageIdx(i => (i + 1) % galleryImages.length)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/80 hover:bg-white rounded-full shadow-md flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity text-gray-700 text-xl">›</button>
                </>
              )}
            </div>
            {/* 썸네일 */}
            {galleryImages.length > 1 && (
              <div className="flex gap-2">
                {galleryImages.map((img, i) => (
                  <button key={i} onClick={() => setSelectedImageIdx(i)}
                    className={`flex-1 aspect-square rounded-xl overflow-hidden border-2 transition-all ${selectedImageIdx === i ? 'border-red-500' : 'border-gray-200 hover:border-gray-300'}`}>
                    <img src={img} alt={`${product.name} ${i+1}`} className="w-full h-full object-cover"
                      onError={(e) => { (e.target as HTMLImageElement).parentElement!.style.display = 'none'; }}/>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 flex flex-col gap-4">
            {product.categoryName && (
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-50 text-red-700 w-fit">
                {product.categoryName}
              </span>
            )}
            <h1 className="text-2xl font-bold text-gray-900 leading-tight">{product.name}</h1>

            <div className="flex items-center gap-2">
              <Stars rating={Math.round(displayRating)} size="sm"/>
              <span className="text-sm font-semibold text-gray-800">{displayRating.toFixed(1)}</span>
              <span className="text-sm text-gray-400">({reviewCount.toLocaleString()}개 리뷰)</span>
            </div>

            <hr className="border-gray-100"/>

            <div>
              {discountRate > 0 && (
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-lg font-bold text-red-600">{discountRate}%</span>
                  <span className="text-gray-400 line-through text-sm">{originalPrice.toLocaleString()}원</span>
                </div>
              )}
              <div className="text-3xl font-black text-gray-900">
                {product.price.toLocaleString()}<span className="text-xl font-semibold ml-1">원</span>
              </div>
            </div>

            <div className="bg-gray-50 rounded-xl p-4 space-y-2 text-sm">
              {isFreeShipping
                ? <div className="flex items-center gap-2"><span className="font-bold text-blue-600">🚀 로켓배송</span><span className="text-gray-500">· 무료배송</span></div>
                : <div className="flex items-center gap-2 text-gray-600"><span>📦 일반배송</span><span className="text-gray-500">· 3,000원</span></div>
              }
              <div className="flex items-center gap-2 text-gray-600"><span>🔄</span><span>7일 이내 무료 반품</span></div>
              <div className="flex items-center gap-2 text-gray-600"><span>🛡️</span><span>안전결제 보장</span></div>
            </div>

            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1.5">
                <div className={`w-2 h-2 rounded-full ${inStock?'bg-green-500':'bg-red-500'}`}/>
                <span className={`text-sm font-medium ${inStock?'text-green-700':'text-red-700'}`}>
                  {inStock?`재고 ${product.stockQuantity}개`:'품절'}
                </span>
              </div>
              {inStock && (
                <div className="flex items-center rounded-xl border border-gray-200 overflow-hidden">
                  <button onClick={()=>setQuantity(Math.max(1,quantity-1))} className="w-10 h-10 flex items-center justify-center text-gray-600 hover:bg-gray-50 text-xl font-bold">−</button>
                  <span className="w-12 text-center font-semibold text-gray-900">{quantity}</span>
                  <button onClick={()=>setQuantity(Math.min(product.stockQuantity,quantity+1))} className="w-10 h-10 flex items-center justify-center text-gray-600 hover:bg-gray-50 text-xl font-bold">+</button>
                </div>
              )}
              {inStock && quantity > 1 && (
                <span className="text-sm text-gray-500">합계: <span className="font-bold text-gray-900">{(product.price*quantity).toLocaleString()}원</span></span>
              )}
            </div>

            <div className="flex gap-3 mt-auto">
              <button onClick={()=>setWished(!wished)}
                className={`w-12 h-12 rounded-xl border-2 flex items-center justify-center transition-all ${wished?'border-red-500 bg-red-50 text-red-500':'border-gray-200 text-gray-400 hover:border-red-300'}`}>
                <svg className="w-5 h-5" fill={wished?'currentColor':'none'} stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
                </svg>
              </button>
              <button onClick={handleAddToCart} disabled={!inStock}
                className="flex-1 py-3 rounded-xl font-bold border-2 border-red-600 text-red-600 hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors">장바구니</button>
              <button onClick={()=>{handleAddToCart();router.push('/orders/new');}} disabled={!inStock}
                className="flex-1 py-3 rounded-xl font-bold bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors">바로 구매</button>
            </div>
          </div>
        </div>

        {/* 탭 */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="flex border-b border-gray-100">
            {(['description','reviews'] as const).map(tab => (
              <button key={tab} onClick={()=>setActiveTab(tab)}
                className={`flex-1 py-4 text-sm font-semibold transition-colors ${activeTab===tab?'text-red-600 border-b-2 border-red-600 bg-red-50/30':'text-gray-500 hover:text-gray-700'}`}>
                {tab==='description'?'상품 설명':`리뷰 (${reviewCount})`}
              </button>
            ))}
          </div>
          <div className="p-6">
            {activeTab==='description' ? (
              <p className="text-gray-700 leading-relaxed whitespace-pre-line text-base">{product.description||'상품 설명이 없습니다.'}</p>
            ) : (
              <div className="space-y-5">
                {summary && summary.totalCount > 0 && (
                  <div className="bg-gray-50 rounded-xl p-5 flex items-start gap-8">
                    <div className="text-center">
                      <div className="text-5xl font-black text-gray-900">{displayRating.toFixed(1)}</div>
                      <Stars rating={Math.round(displayRating)} size="md"/>
                      <div className="text-sm text-gray-500 mt-1">{reviewCount}개</div>
                    </div>
                    <div className="flex-1 space-y-1.5">
                      {[5,4,3,2,1].map(star => {
                        const count = summary.ratingDistribution?.[star]||0;
                        const pct = summary.totalCount>0?(count/summary.totalCount)*100:0;
                        return (
                          <div key={star} className="flex items-center gap-2">
                            <span className="text-xs text-gray-500 w-5 text-right">{star}★</span>
                            <div className="flex-1 bg-gray-200 rounded-full h-1.5">
                              <div className="bg-yellow-400 h-1.5 rounded-full" style={{width:`${pct}%`}}/>
                            </div>
                            <span className="text-xs text-gray-400 w-6">{count}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                <div className="flex justify-end">
                  <button onClick={()=>setShowReviewForm(!showReviewForm)}
                    className={showReviewForm?'btn-secondary btn-sm':'btn-primary btn-sm'}>
                    {showReviewForm?'취소':'리뷰 작성'}
                  </button>
                </div>

                {showReviewForm && (
                  <form onSubmit={handleSubmitReview} className="bg-gray-50 rounded-xl p-5 space-y-4 animate-fadeInUp">
                    <h3 className="font-bold text-gray-900">리뷰 작성</h3>
                    <div><label className="form-label">평점</label>
                      <Stars rating={reviewForm.rating} size="lg" interactive onRate={r=>setReviewForm({...reviewForm,rating:r})}/>
                    </div>
                    <div><label className="form-label">제목 (선택)</label>
                      <input value={reviewForm.title} onChange={e=>setReviewForm({...reviewForm,title:e.target.value})} className="form-input" placeholder="리뷰 제목"/>
                    </div>
                    <div><label className="form-label">내용 *</label>
                      <textarea value={reviewForm.content} onChange={e=>setReviewForm({...reviewForm,content:e.target.value})}
                        className="form-input resize-none" rows={4} required placeholder="상품 사용 후기를 작성해주세요"/>
                    </div>
                    <button type="submit" disabled={submitting} className="btn-primary">{submitting?'등록 중...':'리뷰 등록'}</button>
                  </form>
                )}

                {reviews.length===0 ? (
                  <div className="text-center py-12 text-gray-400">
                    <div className="text-4xl mb-3">💬</div>
                    <p>아직 리뷰가 없습니다. 첫 번째 리뷰를 작성해보세요!</p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {reviews.map(review => (
                      <div key={review.id} className="border-b border-gray-100 pb-5 last:border-0">
                        <div className="flex items-center justify-between mb-2">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 bg-red-100 rounded-full flex items-center justify-center font-bold text-red-600 text-sm">
                              {review.userName?.charAt(0)||'U'}
                            </div>
                            <div>
                              <div className="text-sm font-medium text-gray-900">{review.userName||'사용자'}</div>
                              <div className="text-xs text-gray-400">{new Date(review.createdAt).toLocaleDateString('ko-KR')}</div>
                            </div>
                          </div>
                          <Stars rating={review.rating} size="sm"/>
                        </div>
                        {review.title && <h4 className="font-semibold text-gray-800 mb-1">{review.title}</h4>}
                        <p className="text-gray-700 text-sm leading-relaxed">{review.content}</p>
                        <button onClick={()=>reviewApi.markHelpful(productId,review.id)}
                          className="mt-2 text-xs text-gray-400 hover:text-gray-600 transition-colors">
                          👍 도움이 됐어요 ({review.helpfulCount||0})
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* 연관 상품 */}
        {relatedProducts.length > 0 && (
          <section className="bg-white rounded-2xl p-6 mt-6">
            <div className="flex items-center justify-between mb-5">
              <div>
                <h2 className="text-xl font-bold text-gray-900">함께 보면 좋은 상품</h2>
                <p className="text-sm text-gray-500 mt-0.5">같은 카테고리의 인기 상품</p>
              </div>
              <a href="/products" className="text-sm font-medium text-red-600 hover:text-red-700 transition-colors flex items-center gap-1">
                전체보기
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </a>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {relatedProducts.map((p: Product) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </section>
        )}
      </div>
      <CartSummary/>
    </main>
  );
}
