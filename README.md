# 📍 Geospatial Service

> **기사 위치 데이터를 실시간으로 수집·가공하고, 반경 검색 및 실시간 전송 제어를 담당합니다.**

## 🛠 Tech Stack
| Category | Technology                     |
| :--- |:-------------------------------|
| **Language** | Java 17                    |
| **Framework** | Spring Boot (WebFlux)                 |
| **Database** | Redis (Dual Instance Strategy: Cache & Storage)  |
| **Messaging** | Apache Kafka (Producer/Consumer), Redis Pub/Sub |
| **Protocol** | WebSocket              |

## 📡 API Specification

### WebSocket
| Method | URI | Description |
| :--- | :--- | :--- |
| `WS` | `/ws/location/{driverId}` | **기사 전용:** 실시간 기사 위치 수집 |

### Location Search (REST)
| Method | URI | Description |
| :--- | :--- | :--- |
| `GET` | `/api/locations/search` | **승객/매칭용:** 특정 반경 내 가용 기사 목록 검색 (Redis Geo) |

## 🚀 Key Improvements

### 1. Redis 물리적 이원화 전략 (Performance & Reliability)
* **Cache Redis (6379):** 휘발성이 강하고 빈번한 쓰기가 일어나는 기사 위치(Geo) 데이터를 처리합니다. 성능을 위해 영속성 옵션을 제거했습니다.
* **Storage Redis (6380)**: 기사의 상태(Hash)를 관리합니다. 배차 정합성을 위해 AOF/RDB (Preamble) 영속성을 활성화하여 위치 데이터와 상태 데이터를 격리했습니다.

### 2. '유령 기사' 원천 차단 (Sidecar TTL Pattern)
* 위치 정보만으로는 기사가 앱을 강제 종료했을 때 이탈 여부를 알기 어렵습니다. 
* 이를 해결하기 위해 위치 업데이트 시마다 Storage Redis에 30초 TTL을 가진 'Active Key'를 갱신합니다. 
* 반경 검색 시 Geo 데이터가 존재하더라도 Active Key가 없는 기사는 '유령 기사'로 판단하여 검색 결과에서 즉시 제외하고 Geo 인덱스에서도 삭제(Lazy Eviction)하는 방어 로직을 구축했습니다.


### 3. 지능형 웹소켓 파이프라인 (Backpressure & Heartbeat)
* **Sampling:** 기사 앱에서 초당 여러번 위치 정보를 보내더라도 `sample(Duration.ofMillis(1000))`을 통해 서버 부하를 방지하고 초당 1회만 처리하도록 조절했습니다.
* **Heartbeat:** 10초 주기 서버 PING 전송 및 클라이언트 PONG 수신 필터를 통해 좀비 커넥션을 30초 내에 강제 종료(Timeout)합니다.

### 4. Kafka 기반 실시간 전송 주기 제어 (Server-Driven Control)
* **상황별 최적화:** 배차 성공 시(`TripMatchedEvent`)에는 기사의 위치를 1초마다 수집하고, 운행 종료 시에는 10초마다 수집하도록 기사 앱의 설정을 실시간으로 바꿉니다.
* **구조:** Kafka 이벤트를 수신하면 Redis Pub/Sub을 통해 해당 기사가 연결된 웹소켓 세션에 설정 변경 메시지(`DriverConfigMessage`)를 즉시 브로드캐스팅합니다.

### 5. Reactive Sequential Processing
* 주변 기사 검색 시 `flatMapSequential`을 사용하여 거리 순으로 정렬된 기사들의 상태를 순차적으로 검증합니다. 
* 이때 특정 기사의 상태 조회가 지연되더라도 전체 시스템이 멈추지 않도록 200ms 타임아웃을 개별 적용하여 가용성을 확보했습니다.



----------

## 아키텍쳐
<img width="2324" height="1686" alt="Image" src="https://github.com/user-attachments/assets/81a25ff9-ee02-4996-80d3-f9217c3b7750" />
