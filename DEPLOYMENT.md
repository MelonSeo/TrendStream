# TrendStream 배포 가이드

> GHCR (GitHub Container Registry) + Docker Compose + EC2 배포

## 아키텍처

```
로컬 개발 환경                         AWS EC2
┌─────────────┐                    ┌─────────────────────────────┐
│ 소스 코드    │                    │  Docker Compose             │
│     ↓       │                    │  ┌─────┐ ┌─────┐ ┌───────┐ │
│ Docker Build│ ──push──→ GHCR ──pull──→│ App │ │ DB  │ │ Kafka │ │
│     ↓       │                    │  └─────┘ └─────┘ └───────┘ │
│ GHCR Push   │                    │  ┌─────┐ ┌───────────────┐ │
└─────────────┘                    │  │Redis│ │    Caddy      │ │
                                   │  └─────┘ └───────────────┘ │
                                   └─────────────────────────────┘
```

---

## 사전 준비

### 필요한 것
- [ ] AWS 계정
- [ ] GitHub 계정
- [ ] Gmail 계정 (앱 비밀번호 발급)
- [ ] Naver API 키
- [ ] Groq/Gemini API 키

### GitHub Personal Access Token 발급
1. https://github.com/settings/tokens 접속
2. **Generate new token (classic)**
3. 권한: `write:packages`, `read:packages`
4. 토큰 복사해서 보관

---

## 1. EC2 인스턴스 생성

### AWS 콘솔에서 EC2 생성

| 항목 | 설정값 |
|------|--------|
| 이름 | `TrendStream-Server` |
| AMI | Ubuntu Server 22.04 LTS |
| 인스턴스 유형 | `t3.medium` (권장) 또는 `t3.small` |
| 키 페어 | 새로 생성 또는 기존 키 사용 |
| 스토리지 | **30GB** gp3 |

### 보안 그룹 (인바운드 규칙)

| 유형 | 포트 | 소스 |
|------|------|------|
| SSH | 22 | 내 IP |
| HTTP | 80 | 0.0.0.0/0 |
| HTTPS | 443 | 0.0.0.0/0 |

---

## 2. EC2 초기 설정

### SSH 접속

```bash
# 키 파일 권한 설정 (최초 1회)
chmod 400 ~/Downloads/your-key.pem

# SSH 접속
ssh -i ~/Downloads/your-key.pem ubuntu@<EC2_PUBLIC_IP>
```

### Docker 설치

```bash
# 패키지 업데이트
sudo apt update && sudo apt install -y ca-certificates curl gnupg

# Docker GPG 키 추가
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Docker 저장소 추가
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 설치
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 현재 사용자에게 Docker 권한 부여
sudo usermod -aG docker $USER

# 재접속 (권한 적용)
exit
```

재접속 후 확인:
```bash
docker --version
docker compose version
```

---

## 3. 로컬에서 Docker 이미지 빌드 & 푸시

### GHCR 로그인

```bash
# 프로젝트 디렉토리로 이동
cd "/path/to/TrendStream"

# GHCR 로그인
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u MelonSeo --password-stdin
```

### 이미지 빌드 & 푸시

> ⚠️ **중요**: Mac (Apple Silicon)에서 빌드 시 반드시 `--platform linux/amd64` 플래그를 사용해야 EC2에서 실행됩니다.

```bash
# 빌드 (5-10분 소요)
docker build --platform linux/amd64 -t ghcr.io/melonseo/trendstream:latest .

# 푸시
docker push ghcr.io/melonseo/trendstream:latest
```

### 버전 태그 사용 (권장)

```bash
# 버전 태그로 빌드
docker build --platform linux/amd64 -t ghcr.io/melonseo/trendstream:v1.0.0 .
docker build --platform linux/amd64 -t ghcr.io/melonseo/trendstream:latest .

# 둘 다 푸시
docker push ghcr.io/melonseo/trendstream:v1.0.0
docker push ghcr.io/melonseo/trendstream:latest
```

---

## 4. EC2에 배포 파일 준비

### 배포 디렉토리 생성

```bash
# EC2에서 실행
mkdir -p ~/trendstream && cd ~/trendstream
```

### docker-compose.yml 생성

```bash
cat << 'EOF' > docker-compose.yml
version: '3.8'

services:
  app:
    image: ghcr.io/melonseo/trendstream:latest
    container_name: trendstream-app
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}
      NAVER_CLIENT_ID: ${NAVER_CLIENT_ID}
      NAVER_CLIENT_SECRET: ${NAVER_CLIENT_SECRET}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      GROQ_API_KEY: ${GROQ_API_KEY}
      AI_PROVIDER: ${AI_PROVIDER:-groq}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
      MAIL_USERNAME: ${MAIL_USERNAME}
      MAIL_PASSWORD: ${MAIL_PASSWORD}
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
      kafka:
        condition: service_started
    networks:
      - trendstream-network
    restart: always

  db:
    image: mysql:8.0
    container_name: trendstream-db
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: trend_stream
    volumes:
      - db-data:/var/lib/mysql
    networks:
      - trendstream-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${DB_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: always

  redis:
    image: redis:7-alpine
    container_name: trendstream-redis
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    networks:
      - trendstream-network
    restart: always

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: trendstream-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      - zookeeper-log:/var/lib/zookeeper/log
    networks:
      - trendstream-network
    restart: always

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: trendstream-kafka
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka-data:/var/lib/kafka/data
    depends_on:
      - zookeeper
    networks:
      - trendstream-network
    restart: always

  caddy:
    image: caddy:2-alpine
    container_name: trendstream-caddy
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - app
    networks:
      - trendstream-network
    restart: always

networks:
  trendstream-network:
    driver: bridge

volumes:
  db-data:
  redis-data:
  zookeeper-data:
  zookeeper-log:
  kafka-data:
  caddy-data:
  caddy-config:
EOF
```

### Caddyfile 생성

```bash
# 도메인 없이 IP로 접속하는 경우
cat << 'EOF' > Caddyfile
:80 {
    reverse_proxy app:8081
}
EOF

# 도메인 있는 경우 (HTTPS 자동 적용)
# cat << 'EOF' > Caddyfile
# your-domain.com {
#     reverse_proxy app:8081
# }
# EOF
```

### .env 파일 생성

```bash
cat << 'EOF' > .env
# Database
DB_PASSWORD=your_secure_password

# Naver API
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret

# AI Provider
AI_PROVIDER=groq
GROQ_API_KEY=your_groq_api_key
GEMINI_API_KEY=your_gemini_api_key

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000

# Mail (Gmail)
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
EOF

# 실제 값으로 수정
nano .env
```

---

## 5. 서비스 실행

### GHCR 로그인 (EC2에서)

```bash
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u MelonSeo --password-stdin
```

### Docker Compose 실행

```bash
cd ~/trendstream

# 이미지 풀 & 실행
docker compose up -d

# 로그 확인
docker compose logs -f app
```

### 상태 확인

```bash
# 모든 컨테이너 상태
docker compose ps

# 앱 헬스체크
curl http://localhost:8081/actuator/health
```

### 외부 접속 확인

브라우저에서 `http://<EC2_PUBLIC_IP>` 접속

---

## 6. 코드 변경 후 재배포

### 로컬에서 (코드 수정 후)

```bash
cd "/path/to/TrendStream"

# 1. 새 이미지 빌드 (Mac에서는 --platform 필수!)
docker build --platform linux/amd64 -t ghcr.io/melonseo/trendstream:latest .

# 2. 푸시
docker push ghcr.io/melonseo/trendstream:latest
```

### EC2에서

```bash
cd ~/trendstream

# 1. 새 이미지 풀
docker compose pull app

# 2. 앱만 재시작 (DB, Kafka 등은 유지)
docker compose up -d app

# 3. 로그 확인
docker compose logs -f app
```

### 한 줄 명령어 (재배포)

```bash
# EC2에서 실행
cd ~/trendstream && docker compose pull app && docker compose up -d app
```

---

## 7. 운영 명령어

### 로그 확인

```bash
# 전체 로그
docker compose logs -f

# 앱 로그만
docker compose logs -f app

# 최근 100줄
docker compose logs --tail 100 app
```

### 서비스 제어

```bash
# 전체 중지
docker compose stop

# 전체 시작
docker compose start

# 전체 재시작
docker compose restart

# 앱만 재시작
docker compose restart app

# 전체 삭제 (볼륨 유지)
docker compose down

# 전체 삭제 (볼륨 포함 - 주의!)
docker compose down -v

MySQL 접속:                                                                                                                                                                      
cd ~/trendstream                                                                                                                                                                 
docker compose exec db mysql -uroot -p trend_stream
```

### 리소스 확인

```bash
# 컨테이너 리소스 사용량
docker stats

# 디스크 사용량
docker system df

# 미사용 이미지 정리
docker image prune -a
```

---

## 8. 트러블슈팅

### 앱이 시작되지 않을 때

```bash
# 로그 확인
docker compose logs app

# 컨테이너 상태 확인
docker compose ps

# DB 연결 확인
docker compose exec db mysql -uroot -p
```

### 메모리 부족

```bash
# 메모리 사용량 확인
free -h

# 스왑 추가 (필요시)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### 이미지 풀 실패

```bash
# GHCR 재로그인
echo "YOUR_TOKEN" | docker login ghcr.io -u MelonSeo --password-stdin

# 이미지 확인
docker images | grep trendstream
```

### 플랫폼 아키텍처 불일치

**증상**:
```
Error response from daemon: no matching manifest for linux/amd64
```

**원인**: Mac (Apple Silicon)에서 빌드한 이미지는 `arm64` 아키텍처이지만, EC2는 `amd64` 필요

**해결**:
```bash
# 로컬에서 다시 빌드 (플랫폼 명시)
docker build --platform linux/amd64 -t ghcr.io/melonseo/trendstream:latest .
docker push ghcr.io/melonseo/trendstream:latest

# EC2에서 다시 풀
docker compose pull app && docker compose up -d app
```

---

## 9. DB 데이터 관리

### SQL 파일을 컨테이너에서 실행하는 방법
> SSH 터미널에서 한글 입력이 안 되므로 로컬에서 SQL 파일을 작성 후 EC2로 전송하여 실행

```bash
# 로컬에서 EC2로 파일 전송
scp -i ~/.ssh/your-key.pem cleanup.sql ubuntu@<EC2_IP>:~/trendstream/

# EC2에서 컨테이너로 복사 후 실행
docker compose cp cleanup.sql db:/tmp/cleanup.sql
docker compose exec db mysql -uroot -pYOUR_DB_PASSWORD --default-character-set=utf8mb4 trend_stream -e "source /tmp/cleanup.sql"
```

> `~/.ssh/`에 키 파일을 두어야 macOS 권한 문제를 피할 수 있음

### 스팸 데이터 정리 (한자/베트남어)

```sql
-- 한자 스팸 삭제 (중국어 광고글)
DELETE nt FROM news_tags nt
INNER JOIN news n ON nt.news_id = n.id
WHERE n.title REGEXP '[\\x{4E00}-\\x{9FFF}]'
   OR n.description REGEXP '[\\x{4E00}-\\x{9FFF}]';

DELETE FROM news
WHERE title REGEXP '[\\x{4E00}-\\x{9FFF}]'
   OR description REGEXP '[\\x{4E00}-\\x{9FFF}]';

-- 베트남어 스팸 삭제
DELETE nt FROM news_tags nt
INNER JOIN news n ON nt.news_id = n.id
WHERE n.title REGEXP '[\\x{1E00}-\\x{1EFF}]';

DELETE FROM news
WHERE title REGEXP '[\\x{1E00}-\\x{1EFF}]';
```

### 특정 소스 데이터 삭제

```sql
-- news_tags 먼저 삭제 후 news 삭제 (FK 제약 때문)
DELETE nt FROM news_tags nt
INNER JOIN news n ON nt.news_id = n.id
WHERE n.source = 'SOURCE_NAME';

DELETE FROM news WHERE source = 'SOURCE_NAME';
DELETE FROM news_stats WHERE source = 'SOURCE_NAME';
```

### Velog 재분석 초기화

```sql
-- aiResult를 NULL로 초기화하면 스케줄러가 자동으로 재분석
UPDATE news SET ai_result = NULL WHERE source = 'Velog';
```

---

## 10. 백업 (선택)

### MySQL 데이터 백업

```bash
# 백업
docker compose exec db mysqldump -uroot -p trend_stream > backup_$(date +%Y%m%d).sql

# 복원
docker compose exec -T db mysql -uroot -p trend_stream < backup_20260208.sql
```

---

## 체크리스트

### 최초 배포
- [ ] EC2 생성 (t3.medium, 30GB)
- [ ] Docker 설치
- [ ] GitHub Token 발급
- [ ] 로컬에서 이미지 빌드 & 푸시
- [ ] EC2에서 GHCR 로그인
- [ ] docker-compose.yml, Caddyfile, .env 생성
- [ ] `docker compose up -d` 실행
- [ ] 브라우저에서 접속 확인

### 코드 변경 후 재배포
- [ ] 로컬: `docker build` & `docker push`
- [ ] EC2: `docker compose pull app && docker compose up -d app`

---

## 참고

- GHCR 이미지: `ghcr.io/melonseo/trendstream:latest`
- Swagger UI: `http://<IP>/swagger-ui.html`
- 헬스체크: `http://<IP>/actuator/health`
