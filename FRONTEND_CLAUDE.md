# TrendStream Frontend

## 1. Project Overview
IT 뉴스를 수집하고 AI로 분석한 결과를 보여주는 웹 서비스의 프론트엔드입니다.
백엔드(Spring Boot)에서 제공하는 REST API를 호출하여 뉴스 데이터를 표시합니다.

## 2. Tech Stack
- **Framework**: Next.js 14+ (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **State Management**: TanStack Query (React Query)
- **HTTP Client**: Axios 또는 Fetch API
- **Deployment**: Vercel

## 3. Backend API Reference

### 3.1 Base URL
- **Development**: `http://localhost:8081`
- **Production**: (배포 후 업데이트)

### 3.2 Endpoints

| Method | Path | Description | Query Params |
|--------|------|-------------|--------------|
| GET | `/api/news` | 최신 뉴스 목록 | `page`, `size` |
| GET | `/api/news/{id}` | 뉴스 상세 조회 | - |
| GET | `/api/news/search` | 키워드 검색 | `keyword`, `page`, `size` |
| GET | `/api/news/popular` | 인기 뉴스 (AI 점수순) | `page`, `size` |

### 3.3 Response Types

#### NewsResponseDto
```typescript
interface NewsResponseDto {
  id: number;
  title: string;
  link: string;
  description: string;
  source: string;
  type: 'NEWS' | 'BLOG' | 'COMMUNITY';
  pubDate: string; // ISO 8601 format: "2026-02-03T00:20:00"
  aiResult: AiResponse | null;
  tags: string[];
}

interface AiResponse {
  summary: string;      // AI가 생성한 3줄 요약
  sentiment: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  keywords: string[];   // 추출된 키워드 (예: ["AI", "Spring"])
  score: number;        // 중요도 점수 (0-100)
}
```

#### Page Response (Spring Data)
```typescript
interface Page<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: {
      sorted: boolean;
      unsorted: boolean;
      empty: boolean;
    };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}
```

### 3.4 API 호출 예시

```typescript
// 최신 뉴스 조회
const response = await fetch('http://localhost:8081/api/news?page=0&size=10');
const data: Page<NewsResponseDto> = await response.json();

// 키워드 검색
const response = await fetch('http://localhost:8081/api/news/search?keyword=AI&page=0&size=10');

// 인기 뉴스
const response = await fetch('http://localhost:8081/api/news/popular?page=0&size=10');

// 상세 조회
const response = await fetch('http://localhost:8081/api/news/1');
const news: NewsResponseDto = await response.json();
```

## 4. Page Structure (권장)

```
app/
├── page.tsx                    # 홈 (최신 뉴스 목록)
├── layout.tsx                  # 공통 레이아웃
├── news/
│   ├── [id]/
│   │   └── page.tsx           # 뉴스 상세 페이지
│   └── search/
│       └── page.tsx           # 검색 결과 페이지
├── popular/
│   └── page.tsx               # 인기 뉴스 페이지
└── components/
    ├── NewsCard.tsx           # 뉴스 카드 컴포넌트
    ├── NewsList.tsx           # 뉴스 목록 컴포넌트
    ├── SearchBar.tsx          # 검색바
    ├── Pagination.tsx         # 페이지네이션
    ├── SentimentBadge.tsx     # 감정 분석 배지 (긍정/부정/중립)
    └── ScoreBadge.tsx         # 중요도 점수 배지
```

## 5. UI/UX Guidelines

### 5.1 핵심 기능
- **뉴스 목록**: 카드 형태로 뉴스 표시 (제목, 요약, 키워드, 점수)
- **검색**: 실시간 검색 또는 버튼 클릭 검색
- **상세 보기**: 전체 내용 + AI 분석 결과 표시
- **무한 스크롤** 또는 **페이지네이션** 선택

### 5.2 표시할 정보
| 컴포넌트 | 표시 항목 |
|---------|----------|
| 뉴스 카드 | 제목, 요약(summary), 키워드 태그, 점수, 발행일 |
| 상세 페이지 | 전체 내용 + 원문 링크 + 감정 분석 + 키워드 |
| 검색바 | 키워드 입력, 검색 버튼 |

### 5.3 감정 분석 표시 (색상 가이드)
- `POSITIVE`: 초록색 (#22c55e)
- `NEGATIVE`: 빨간색 (#ef4444)
- `NEUTRAL`: 회색 (#6b7280)

### 5.4 점수 표시 (색상 가이드)
- 80-100: 빨간색 (HOT)
- 60-79: 주황색
- 40-59: 노란색
- 0-39: 회색

## 6. Environment Variables

```env
# .env.local
NEXT_PUBLIC_API_URL=http://localhost:8081

# .env.production
NEXT_PUBLIC_API_URL=https://your-backend-domain.com
```

## 7. CORS 설정 (백엔드)
프론트엔드 개발 시 CORS 에러가 발생하면, 백엔드에 CORS 설정이 필요합니다.
백엔드 담당자에게 요청하거나, 아래 경로의 파일을 수정하세요:

**백엔드 저장소 경로**: `C:\Users\kyobin\Desktop\study\TrendStream`

필요한 설정:
```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000") // Next.js 개발 서버
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
```

## 8. Development Commands

```bash
# 프로젝트 생성
npx create-next-app@latest trendstream-frontend --typescript --tailwind --app

# 개발 서버 실행
npm run dev

# 빌드
npm run build

# 프로덕션 실행
npm start
```

## 9. Deployment (Vercel)

1. GitHub에 프론트엔드 저장소 push
2. Vercel 연동 (https://vercel.com)
3. Environment Variables 설정 (`NEXT_PUBLIC_API_URL`)
4. 자동 배포 완료

## 10. Related Resources

- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **API Docs (JSON)**: `http://localhost:8081/v3/api-docs`
- **백엔드 저장소**: `C:\Users\kyobin\Desktop\study\TrendStream`
- **백엔드 문서**: `TrendStream/CLAUDE.md`, `TrendStream/TROUBLESHOOTING.md`
