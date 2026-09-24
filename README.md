# Fitness Microservices — Learning Project

Interview-prep build of an AI-powered fitness tracker using a microservices
architecture, based on a Hindi course
("[DAY80-95] Spring Boot Full Stack AI Microservices Project: Kafka, AWS,
Keycloak, OAuth2"). Goal is not just to finish the app — it's to be able to
explain the architecture and code confidently in a technical interview.

## What We're Building

A fitness tracker where a user logs an activity (walk / run / cycle), the
activity is saved immediately, and — separately, in the background — an AI
(Google Gemini) analyzes that activity and generates a personalized
recommendation (pace, calories, improvements, workout plan, safety tips).
The user later opens the app and sees that recommendation.

The fitness app itself is just the vehicle. The real subject matter is
microservices architecture: independent services with their own databases,
service discovery, a single gateway entry point, two different
inter-service communication styles (sync and async), centralized config,
and OAuth2-based security.

## Architecture

```
                    Keycloak (Who are you?)
                          |  login -> access token
                          v
                       React
                          |  every request: Authorization: Bearer <token>
                          v
                    API Gateway (Where does this go? Are you allowed?)
          +----------------+-----------------+
          v                v                 v
   User Service    Activity Service     AI Service
          |                |                 ^
     PostgreSQL             |  publish        | consume
                             v                 |
                          MongoDB           Kafka ---+
                             |                        |
                    (activity stored)             Gemini API
                                                        |
                                          AI Recommendation -> MongoDB
                                                        |
                                            (React fetches later)

  Supporting, off the direct request path:
  Eureka        -> "Where is service X running?"
  Config Server  -> "What configuration should you use?"
```

### The two flows that matter most

**Normal (synchronous) request path**

```
React -> API Gateway -> Microservice -> Database
```

Example: Activity Service needs to know *right now* whether a user exists,
so it calls User Service directly and waits for the answer.

```
Activity Service -> User Service : "Does user 123 exist?"
User Service     -> Activity Service : "Yes"
```

If User Service is down, this call fails — that's an accepted trade-off of
synchronous communication.

**AI (asynchronous) event path**

```
Activity Service -> Kafka -> AI Service -> Gemini -> AI Recommendation -> Database -> React
```

Activity Service saves the activity and publishes an event to Kafka, then
returns immediately — it does not wait for AI processing. AI Service
consumes the event later, calls Gemini, stores the structured
recommendation, and React fetches it on a later request.

## Microservices & Responsibilities

| Service | Responsibility | Database |
|---|---|---|
| User Service | Manage users; validated by Activity Service before an activity is accepted | PostgreSQL |
| Activity Service | Manage fitness activities; validates user synchronously; publishes activity events to Kafka | MongoDB |
| AI Service | Consumes activity events from Kafka; calls Gemini; stores AI recommendations | MongoDB |
| API Gateway | Single external entry point; routes requests; validates auth tokens | — |
| Eureka Server | Service registry / service discovery | — |
| Config Server | Centralized configuration for all services | — |

## Technologies Used — and Why

| Technology | Why we use it |
|---|---|
| **Spring Boot** | Backend framework for each service — fast to bootstrap REST APIs, strong ecosystem for JPA, Kafka, security. |
| **React + Vite** | Frontend UI. Vite chosen for fast dev builds. Kept intentionally simple — the project prioritizes backend architecture over UI polish. |
| **Microservices architecture** | Splits the system by business capability (users, activities, AI) instead of one monolith, so each service can be built, deployed, scaled, and owned independently. |
| **PostgreSQL** (User Service) | User data is naturally relational (structured, constrained, transactional) — a relational DB fits that shape well. |
| **MongoDB** (Activity & AI Service) | Activity and AI-recommendation data is more document-shaped and can evolve flexibly — demonstrates that each service can pick the persistence technology that fits its own data, not a shared schema. |
| **Eureka** | As the number of services grows, hardcoding URLs (`http://localhost:8081`) becomes unmanageable. Services register with Eureka and discover each other by logical name instead. |
| **Spring Cloud Gateway** | The outside world (React, Postman) should not need to know the internal address of every microservice. The Gateway is the single entry point — it routes by path (`/users/**`, `/activities/**`, `/ai/**`) and is also where auth is enforced before a request reaches internal services. |
| **Spring Cloud Config Server** | Configuration (DB credentials, Kafka settings, external API keys) would otherwise be duplicated and drift across every service. Config Server centralizes it in one place, ideally backed by a Git repo. |
| **Apache Kafka** | AI processing (calling Gemini) is slower than saving an activity and depends on an external API. Using Kafka lets Activity Service publish an event and return immediately, instead of making the user's request wait on AI processing. This is the asynchronous, decoupled counterpart to the synchronous User Service validation call. |
| **Google Gemini API** | Generates the actual AI fitness recommendation from structured activity data. Asked to return a fixed JSON shape (analysis, pace, calories, improvements, suggestions, workout plan, safety guidelines) so the backend can reliably parse and store it instead of handling free-form text. |
| **Keycloak (OAuth2)** | Provides authentication (who are you) and authorization (are you allowed). Issues access tokens on login; the Gateway validates `Authorization: Bearer <token>` on every request before routing it, so security logic isn't duplicated inside every microservice. |
| **Docker** | Used to run infrastructure dependencies locally (Kafka, Keycloak) without manual installation. |

## Why Microservices (Not a Monolith) — Core Interview Answer

- **Service ownership**: each service owns its own data and database; no
  service reaches into another's tables.
- **Independent technology choice**: User Service uses PostgreSQL because
  user data is relational; Activity/AI Service use MongoDB because their
  data is document-shaped. A monolith would typically share one database.
- **Independent deployability**: services can be built, run, and
  (eventually) scaled separately.
- **Failure isolation**: if AI Service is down, activities can still be
  created and saved — only the AI recommendation is delayed, because that
  path is asynchronous (Kafka) rather than a blocking call.
- **Communication style matches the requirement**: synchronous where an
  immediate answer is required (user validation), asynchronous where the
  work can happen in the background (AI recommendation generation).

## Learning Plan

Goal: understand the architecture and code well enough to explain it in a
technical interview — not just get it running.

### Day 1 — 1 hour (conceptual, minimal code)

| Time | Focus |
|---|---|
| 15 min | Monolith vs Microservices — why split, using User + Activity Service as the concrete example |
| 15 min | Service ownership / database-per-service — why Postgres here, Mongo there |
| 15 min | Synchronous communication — Activity Service calling User Service; what "waiting" means; what happens if User Service is down |
| 15 min | Asynchronous communication (intro only) — just enough to contrast with sync; deep dive on Day 2 |

### Day 2 — 4–5 hours (concepts + hands-on implementation)

| Time | Focus |
|---|---|
| 30 min | Recap Day 1 + Eureka (service discovery) |
| 45 min | API Gateway — concept + routing, minimal hands-on |
| 20 min | Config Server — mostly conceptual, light hands-on |
| 60 min | Kafka deep dive + hands-on: producer in Activity Service, consumer in AI Service |
| 30 min | Gemini integration — prompt structure, why structured JSON, storing the response |
| 40 min | Keycloak + Gateway security — conceptual depth, minimal hands-on (one protected endpoint working end to end) |
| remaining | React walkthrough (conceptual, skip full build) OR reinforce whichever concept was weakest |

Pace flexes live: concepts that click fast get banked time; concepts that
are sticky get more time, since understanding takes priority over finishing.

### Concepts — Essential vs Secondary vs Out of Scope

**Essential (interview-critical)**
1. Monolith vs microservices — why split at all
2. Service ownership / database-per-service
3. Synchronous inter-service calls (Activity → User Service)
4. Asynchronous messaging via Kafka (Activity → AI Service) and why AI uses it
5. Service discovery (Eureka)
6. API Gateway — single entry point, routing
7. Centralized config (Config Server)
8. AuthN vs AuthZ, OAuth2 access tokens (Keycloak)
9. Gateway-level security (token validated before routing)
10. Layered structure per service: Controller → Service → Repository → Entity/DTO, and why DTOs exist
11. The full end-to-end flow (both flows above), explainable without notes

**Good to know / secondary**
12. Spring Boot mechanics as applied here (JPA vs Spring Data MongoDB)
13. Kafka topic/producer/consumer config specifics
14. Gemini prompt structuring for predictable JSON
15. React → Gateway fetch with a bearer token

**Explicitly out of scope for this timeline**
- UI styling/design polish
- Docker/AWS deployment
- Refresh token rotation, multi-realm Keycloak setup
- MySQL vs PostgreSQL tradeoffs (just know it's swappable)
- Production-grade error handling/observability

## Planned Project Structure

```
fitness-microservices/
├── user-service/
├── activity-service/
├── ai-service/
├── api-gateway/
├── eureka-server/
├── config-server/
└── frontend/
```

---

*Source material: cleaned English transcripts of the course, organized into
Parts 1–3 covering introduction/architecture, Eureka/Gateway/Config
Server/Kafka, and AI/Keycloak/React/final integration.*
