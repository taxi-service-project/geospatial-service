# 📍 Geospatial Service (LBS)

> **대규모 기사 위치 데이터를 실시간으로 수집하고 반경 검색을 제공합니다.**

## 🛠 Tech Stack
| Category | Technology                     |
| :--- |:-------------------------------|
| **Language** | **Java 17**                    |
| **Framework** | Spring WebFlux                 |
| **Database** | Redis (Dual Instance Strategy) |
| **Protocol** | WebSocket, Kafka               |

## 📡 API Specification

| Method | URI | Description |
| :--- | :--- | :--- |
| `WS` | `/ws/location/{driverId}` | 기사 위치 실시간 수신 (WebSocket) |
| `GET` | `/api/locations/nearby` | 반경 내 기사 검색 (Redis Geo) |

## 🚀 Key Improvements
* **Redis 이원화:** 위치 정보(Cache, 6379, 영속성 OFF)와 상태 정보(Storage, 6380, AOF/RDB ON)를 물리적으로 분리.
* **Sidecar TTL Pattern:** `driver_active` 키를 활용한 Lazy Eviction으로 '유령 택시' 문제 해결.
* **Server-Driven Control:** Kafka 이벤트를 기반으로 기사 앱의 위치 전송 주기(1초/10초) 원격 제어.



----------

## 아키텍쳐
<img width="2324" height="1686" alt="Image" src="https://github.com/user-attachments/assets/81a25ff9-ee02-4996-80d3-f9217c3b7750" />
