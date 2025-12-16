# darkintel-crawler

DarkIntel 백엔드의 **ingest API**(`/v1/ingest/raw`)로 외부(OSINT/다크웹/랜섬웨어 유출 포스트 등) 데이터를 전달하기 위한 크롤러 러너입니다.

이 프로젝트는 **서버리스 아키텍처(Lambda + EventBridge + DynamoDB PAY_PER_REQUEST)** 를 기반으로,
운영 비용을 최소화하면서도 주기 실행·중복 방지·상태 관리가 가능하도록 설계되었습니다.

---

## 무엇을 하는가

- 설정(TOML)에 정의된 여러 소스를 순회하며 페이지를 수집하고
- 소스별 파서(`parser_type`)로 필요한 데이터만 추출·정규화한 뒤
- DynamoDB에 소스 상태 / 문서 중복 / 락 / 스케줄 정보를 저장하고
- 최종적으로 DarkIntel 백엔드의 ingest API로 데이터를 전송합니다.

---

## 현재 실행 방식 요약

- **자동 실행 (스케줄)**  
  EventBridge가 주기적으로 `darkweb-crawler-runner` Lambda를 호출

- **수동 실행 (즉시 실행)**  
  Admin API Lambda(HTTP API)가 crawler Lambda를 호출하여 즉시 실행

---

## 구성 요소

### Lambda

- **Crawler Lambda**
  - 이름: `darkweb-crawler-runner`
  - 역할: 크롤링 실행, 중복 체크, 상태 저장, ingest API 호출
  - Handler: `com.darkintel.crawler.lambda.CrawlerLambdaHandler::handleRequest`

- **Admin API Lambda**
  - 이름: `darkweb-crawler-admin-api`
  - 역할: 즉시 실행(run-now), 스케줄 관리
  - Handler: `com.darkintel.crawler.lambda.AdminApiLambdaHandler::handleRequest`

### DynamoDB (PAY_PER_REQUEST)

| 테이블명 | 역할 |
|--------|------|
| darkweb-crawler_source_state | 소스별 크롤링 상태 |
| darkweb-crawler_documents | 문서 중복 체크 |
| darkweb-crawler_locks | 분산 락 |
| darkweb-crawler_schedule | 소스별 실행 스케줄 |

---

## 설정 파일

### 로컬 실행용: `config.toml`

```bash
java -jar build/libs/darkweb-crawler-all.jar ./config.toml
```

### Lambda 실행용: `config.lambda.toml`

Lambda ZIP 내부 `/var/task/config.lambda.toml` 경로에 위치해야 합니다.

---

## 로컬 실행

```bash
./gradlew shadowJar
java -jar build/libs/darkweb-crawler-all.jar ./config.toml
```

---

## AWS Lambda 배포 요약

1. JAR 빌드
```bash
./gradlew shadowJar
```

2. ZIP 생성
```bash
mkdir -p build/lambda
zip -j build/lambda/darkweb-crawler-crawler.zip build/libs/darkweb-crawler-all.jar config.lambda.toml
zip -j build/lambda/darkweb-crawler-admin-api.zip build/libs/darkweb-crawler-all.jar config.lambda.toml
```

3. Terraform 적용
```bash
cd infra
terraform init
terraform apply
```

---

## 데이터 저장 위치

- DynamoDB: 상태 / 중복 / 락 / 스케줄
- DarkIntel Backend DB: 크롤링 결과(RawDocument, Incident, Alert)

---

## 참고

- 운영 시 실제 사이트에서 직접 크롤링
- Redis 없이 DynamoDB 락으로 동시성 제어
