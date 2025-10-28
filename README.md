# MSA 기반 Taxi 호출 플랫폼 - Geospatial Service

Taxi 호출 플랫폼의 **지리 공간 데이터 처리**를 담당하는 마이크로서비스입니다. 기사의 실시간 위치를 **Redis Geospatial**에 저장/업데이트하고, 특정 지점 주변의 기사를 검색하는 기능을 제공합니다. 또한, 위치 업데이트 시 Kafka로 이벤트를 발행합니다. Spring WebFlux 기반의 Reactive 스택으로 구현되었습니다. 

## 주요 기능

* **기사 위치 업데이트:**
    * (내부 호출) 기사의 위도, 경도 정보를 받아 Redis Geospatial에 저장합니다.
    * 위치 업데이트 시 `DriverLocationUpdatedEvent` 메시지를 Kafka 토픽으로 발행합니다.
* **주변 기사 검색 (API Endpoint):**
    * `GET /api/locations/search?longitude={lon}&latitude={lat}&radius={radiusKm}`
    * 주어진 좌표(`longitude`, `latitude`)와 반경(`radiusKm`) 내에 있는 기사 목록과 거리를 Redis Geospatial (`GEORADIUS`)을 이용해 조회하여 반환합니다.

## 기술 스택 (Technology Stack)

* **Language & Framework:** Java, Spring Boot, **Spring WebFlux**
* **Geospatial Database:** **Spring Data Reactive Redis (Geospatial)**
* **Messaging:** Spring Kafka (Producer)
