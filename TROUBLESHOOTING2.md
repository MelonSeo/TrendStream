# TROUBLESHOOTING 2

## 1. Kafka Consumer 속도 제한과 AI 배치 스케줄러 간 불일치

### 증상
- AI 배치 스케줄러가 30초마다 5개를 처리하려 하지만, 실제로 DB에는 2개만 쌓여있음
- Kafka Consumer가 1개/15초로 소비하므로, 30초 동안 최대 2개만 DB에 적재
- 배치 크기 5에 비해 실제 처리 가능 건수가 부족하여 비효율 발생

### 원인
- **Rate Limit 제어가 두 곳에서 중복**
  - `KafkaConfig`: `MAX_POLL_RECORDS=1` + `idleBetweenPolls=15초` (Consumer 소비 속도 제한)
  - `NewsAnalysisScheduler`: `fixedDelay=30초` + `BATCH_SIZE=5` (AI 호출 속도 제한)
- 원래 `idleBetweenPolls=15초`는 Consumer에서 직접 AI를 호출하던 시절의 Rate Limit 보호 장치
- AI 분석이 Scheduler로 분리된 이후에도 Consumer 속도 제한이 그대로 남아있었음

### 해결

#### KafkaConfig.java - Consumer 속도 제한 완화
```java
// AS-IS
config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
factory.getContainerProperties().setIdleBetweenPolls(15000);

// TO-BE
config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
factory.getContainerProperties().setIdleBetweenPolls(1000);
```

#### NewsAnalysisScheduler.java - 배치 크기 조정
```java
// AS-IS
private static final int BATCH_SIZE = 5;

// TO-BE
private static final int BATCH_SIZE = 3;
```

### 설계 원칙: 역할 분리
| 담당 | 역할 | Rate Limit |
|------|------|-----------|
| **KafkaConfig** | Kafka → DB 적재 속도 | 방어적 1초 간격 (부하 방지) |
| **Scheduler** | AI API 호출 주기 + 배치 크기 | 30초마다 3개 (Gemini Rate Limit 제어) |

---

## 2. 백로그가 쌓이는 위치에 대한 고려

### 논점
- Consumer 속도 제한을 풀면, 처리 못한 뉴스가 DB에 계속 쌓이지 않는가?

### 결론
- 속도 제한이 있든 없든, AI 처리 속도를 넘는 유입이 있으면 백로그는 발생
- **Kafka에 쌓이는 것보다 DB에 쌓이는 것이 유리**:
  - 사용자에게 AI 분석 전이라도 뉴스 목록 노출 가능
  - Kafka retention 기간과 무관하게 영구 보존
  - 장애 복구 시 `aiResult=null` 재조회로 단순하게 처리

### 유입/처리 속도 계산
```
[유입] 5분마다: 5 키워드 x 최대 10개 = 최대 50개 (중복 제외 시 훨씬 적음)
[처리] 3개/30초 = 6개/분 = 30개/5분
→ 정상 운영 시 충분히 따라잡음
```

---

## 3. Kafka의 역할 정리

### "속도 조절이 없으면 Kafka 의미가 없는 것 아닌가?"

Kafka의 핵심 가치는 속도 조절이 아니라 **안정성과 확장성**:

| 가치 | 설명 |
|------|------|
| **장애 복원** | Consumer/DB가 죽어도 메시지가 Kafka에 보존됨 |
| **비동기 분리** | Producer는 Kafka에 넣고 끝, Consumer 상태를 모름 |
| **확장성** | Consumer Group 추가로 알림, 통계 등 병렬 처리 가능 |

```
                         ┌─ [Group A] NewsConsumer      → DB 저장
Producer → Kafka 토픽 ───┼─ [Group B] NotiConsumer      → 푸시 알림 (향후)
                         └─ [Group C] AnalyticsConsumer  → 통계 집계 (향후)
```