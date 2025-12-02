# darkintel-crawler

DarkIntel 백엔드의 **ingest API**로 데이터를 보내기 위한 다크웹 크롤러입니다.

- 정의된 여러 소스를 순회하며 HTML을 가져오고
- 내용을 파싱하여 **정규화된 문서 모델**로 변환한 뒤
- **DynamoDB**에 크롤 상태·문서 중복 여부를 저장하고
- (옵션) **Redis**를 이용해 레이트 리밋/분산 락을 수행하며
- 최종적으로 **DarkIntel /v1/ingest/raw** API로 전송합니다.

로컬 Docker 환경, 단순 JVM 실행, AWS Lambda 같은 배치 실행 환경에서 돌릴 수 있도록 설계된 경량형 크롤링 러너입니다.

## 주요 기능

- TOML 기반 **멀티 소스 크롤링**
- HTML 파싱(BASIC)
- 정규화 문서 전달 → ingest API
- DynamoDB 문서 중복 체크
- DynamoDB 소스 상태 저장
- Redis 기반 RateLimiter / Distributed Lock (옵션)
- Fatal / Retriable 예외 분리 및 재시도 로직

## 로컬 Docker 실행

### 사전 준비
- Docker / Docker Compose

### 실행 절차

#### 1. 이미지 및 fat JAR 빌드

```
docker compose build
```

#### 2. 전체 스택 실행 (crawler + Redis + DynamoDB Local)

```
docker compose up
```

### 설정 파일

예시 설정을 복사해 수정:

```
cp config.example.toml config.toml
```

필수 수정값:

```toml
backend_base_url = "https://your-darkintel-backend/v1"
backend_api_token = "YOUR_TOKEN_HERE"
```

크롤러 컨테이너는 `/app/config.toml`을 찾습니다.  
필요하면 compose의 마운트 경로를 `config.toml`로 변경하세요.

fat JAR는 다음 경로에 생성됩니다:

```
build/libs/darkweb-crawler-all.jar
```

## Docker 사용 시 자주 쓰는 명령

```
docker compose build --no-cache
docker compose logs -f darkweb-crawler
docker compose down
```

## Docker 없이 실행

### 빌드

```
./gradlew shadowJar
```

### 실행

```
java -jar build/libs/darkweb-crawler-all.jar /path/to/config.toml
```

## config.toml 예시

```toml
backend_base_url = "https://your-darkintel-backend/v1"
backend_api_token = "YOUR_TOKEN_HERE"

[scheduler]
concurrency = 8
request_timeout_seconds = 15

[[sources]]
id = "9ca59a41-8474-4203-83a2-f5c5e8bdd516"
name = "Demo Ransom Blog"
base_url = "http://example.com/leak-demo"
parser_type = "BASIC"
crawl_interval_minutes = 60
```

## 설정 설명 요약

| 필드 | 설명 |
|------|------|
| backend_base_url | ingest API의 base URL |
| backend_api_token | Bearer 인증 토큰 |
| scheduler.concurrency | 병렬 크롤링 수 |
| scheduler.request_timeout_seconds | HTTP timeout |
| sources | 크롤링할 개별 소스 목록 |

## 환경 변수 (런타임 오버라이드)

| 환경 변수 | 설명 |
|----------|------|
| DARKWEB_CONFIG_PATH | 설정 파일 경로 |
| DARKWEB_AWS_REGION | AWS 리전 |
| DARKWEB_REDIS_URI | Redis URI |
| DARKWEB_USE_REDIS_RATELIMITER | Redis 레이트리미터 사용 여부 |
| DARKWEB_USE_REDIS_LOCK | Redis 락 사용 여부 |

Redis 없는 저비용 AWS 구성:

```
DARKWEB_USE_REDIS_RATELIMITER=false
DARKWEB_USE_REDIS_LOCK=false
```

## DynamoDB 테이블 구성

| 테이블명 | 역할 |
|---------|------|
| darkintel_crawler_source_state | 소스별 크롤 성공/실패 상태 |
| darkintel_crawler_documents | 문서 중복 여부 체크 |
| darkintel_crawler_locks | DynamoDB 기반 분산 락 |

## AWS 배포 전략

### 저비용 운영

- Lambda + EventBridge 스케줄러
- DynamoDB 온디맨드
- Redis/ElastiCache 없음
- NAT Gateway 필요 없음

### 고트래픽 운영

- ECS Fargate 서비스로 전환
- 옵션: ElastiCache Redis (RateLimiter/Lock 전용)
- 동일 코드 그대로 사용 가능
