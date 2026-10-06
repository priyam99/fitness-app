# Fitness Microservices — Revision Notes

Q&A log for interview prep. Short answers only — see README.md for full
architecture detail.

---

## Monolith vs Microservices

**Q: What's the actual difference between microservices and organizing a
monolith into packages?**
A: Deployment/runtime isolation, not code organization. A monolith is one
process — redeploying any part means redeploying everything, and a crash
anywhere takes the whole app down. Microservices are separate processes —
each can be deployed, restarted, or crash independently.

**Q: If Activity Service adds an activity and AI Service is slow/down, what
happens to the user?**
A: Nothing bad — Activity Service saves the activity and fires a Kafka
event, then returns immediately. It doesn't wait for AI Service. Only the
AI recommendation is delayed; the core feature works fine.

**Q: What new problems appear once a function call becomes a network call
between services?**
A: The other service can be down, the call can be slow/time out, responses
can get lost (partial failure), API contracts can drift out of sync
without a compiler catching it, and you need new coordination machinery
(service discovery, retries, gateways) that a monolith never needed.

---

## Service Ownership / Database-per-Service

**Q: Does database-per-service mean every service must use a different DB
technology?**
A: No — common misconception. The rule is about **access boundaries**:
no service may directly read/write another service's database, regardless
of whether they use the same tech or different tech. (Activity Service and
AI Service both use MongoDB, but each owns its own separate database.)

**Q: Why PostgreSQL for User Service but MongoDB for Activity/AI Service?**
A: Data shape. User data is fixed-structure, relational, needs constraints
(unique email) — fits SQL. Activity/AI data is more flexible/document-shaped
— fits MongoDB. Each service picks the tech that fits its own data.

**Q: If AI Service needs an activity's duration/type, how should it get
that data?**
A: From the Kafka message Activity Service already published — never by
querying Activity Service's MongoDB directly. Direct DB access would
violate service ownership.

---

## Synchronous vs Asynchronous Communication

- **Sync** (Activity Service → User Service): needs an answer right now
  ("does this user exist?"). Caller waits. If User Service is down, the
  call fails.
- **Async** (Activity Service → Kafka → AI Service): work can happen later
  ("generate a recommendation"). Caller doesn't wait. A slow/down AI
  Service doesn't block activity creation.

---

## Entity, Repository, DTO, Service, Controller (User Service)

**Q: How does `userRepository.save()` work if `UserRepository` is just an
interface with no method bodies?**
A: Spring generates a proxy implementation class at runtime (dynamic
proxy) that translates each method call into real SQL via Hibernate →
JDBC driver → Postgres.

**Q: Why does the PostgreSQL driver dependency have `scope=runtime`?**
A: Your code never directly imports/calls PostgreSQL driver classes — you
only talk to JPA's abstractions. The driver is only needed when the app
actually runs and opens a real DB connection, not at compile time.

**Q: What's the actual risk of exposing the `User` Entity directly in a
Controller instead of a DTO?**
A: A client could include an `id` field in the request JSON (e.g.
`{"id": 1, ...}`). Since the id is null → insert, but a non-null id makes
Hibernate treat it as an UPDATE — an attacker could overwrite an existing
user's row just by guessing/sending its id. DTOs prevent this by simply
not having an `id` field on the input shape.

**Q: One-sentence rule for what fields go in a DTO?**
A: Include exactly what the receiver of that specific message is allowed
to provide (input DTO) or allowed to see (output DTO) — not more, not
less, and the two sets can differ completely.

**Q: Repository vs Service — who does the actual "if" logic?**
A: Repository = a tool that answers yes/no questions about data (e.g.
`existsByEmail`), no decision logic of its own. Service = the decision
maker that calls the Repository and decides what to do with the answer
(reject, proceed, save, build a response).

**Q: Why does `@Valid` go on the Controller's parameter, not inside the
Service method?**
A: To reject bad input at the earliest possible point — before it ever
reaches business logic or touches the database. Validation annotations
on a DTO (`@NotBlank`, `@Email`) do nothing by themselves; `@Valid` is
the switch that actually activates and enforces them.

**Q: What does `@GetMapping("/{id}")` + `@PathVariable Long id` actually
do?**
A: `{id}` is a placeholder in the URL pattern. `@PathVariable` captures
whatever value is in that URL position and converts it into the typed
Java parameter (e.g. `/api/users/5` → `id = 5L`).

**Q: Why doesn't `getUserById` need a DTO the way `register` does?**
A: DTOs exist to structure/validate multi-field data arriving in a
request *body*. A single simple value from the URL path isn't a
multi-field object — nothing to structure.

**Q: For a list of N users, how many times does `.map(this::toResponse)`
call `toResponse`?**
A: N times — once per element in the list, transforming each `User` into
one `UserResponse`.

**Q: Why extract repeated Entity→DTO copying into one `toResponse()`
helper instead of duplicating it in `register`, `getUserById`,
`getAllUsers`?**
A: So a future field change (e.g. adding `fullName`) is fixed in exactly
one place, instead of needing to remember and update N duplicated copies
— prevents silent drift/inconsistency across endpoints as code evolves.

---

## Component Scanning

**Q: What actually breaks if `@SpringBootApplication` sits in a different
package tree than your Controllers/Services (e.g. `user_service` instead
of `com.fitness.userservice`)?**
A: `@SpringBootApplication` only scans its own package + sub-packages by
default. Anything outside that tree is never registered as a Spring bean
— the app starts with no errors, but the endpoints silently don't exist.
Calling them returns 404, not a validation/business error.

**Q: The rule for where to place the main class?**
A: At the top/root of your package tree — every other package
(controller, service, repository, model, dto) should be a sub-package
underneath it.

---

## Passwords / Hashing (flagged as "good to know", not yet implemented)

**Q: Why never store plaintext passwords?**
A: If the database is breached, every real password is exposed as-is.

**Q: Why does login hash the typed password and compare hashes, instead
of decrypting the stored hash?**
A: Hashing is one-way by design — there's no decrypt operation. The only
way to verify is: hash what was just typed, compare to the stored hash.

(Status: our `User.password` is currently plaintext — deliberately, since
real auth responsibility shifts to Keycloak later in this project.)

---

## Docker

**Q: Image vs container?**
A: Image = downloaded blueprint/template (e.g. `mongo:latest`), reusable.
Container = a running (or stopped) *instance* of that image — like a
class vs an object.

**Q: If you `docker stop` then `docker start` the same container, is data
lost?**
A: No — data persists. Stopping is like powering off a computer; the
container's storage stays intact. Data is only lost if the container
itself is deleted (`docker rm`) without a volume backing it.

**After a laptop restart — startup checklist:**
1. Open Docker Desktop manually (Windows key → "Docker Desktop" → Enter).
   There's no reliable terminal command to launch the Docker Desktop GUI
   itself on Windows — wait ~20-40s for the whale icon in the system tray
   to stop animating before running any `docker` command.
2. Start the three infra containers (they already exist, just stopped):
   ```
   docker start mongodb kafka keycloak
   ```
3. Start Spring Boot services in order, ~10-15s apart: Eureka Server →
   User Service → Activity Service → API Gateway → AI Service.
4. Start the frontend dev server: `cd fitness-microservices/frontend && npm run dev`
   (serves at `http://localhost:5173`).

---

## Secrets / Environment Variables

**Q: Why move the DB password from `application.yml` into an environment
variable (`${DB_PASSWORD}`)?**
A: The repo is public on GitHub. A hardcoded password in a committed file
is visible to anyone. `${DB_PASSWORD}` makes the file safe to publish —
the real value is supplied only on the local machine (IntelliJ run
config), never written to any tracked file.

**Q: Why is `target/` safe to leave with an old plaintext value in it?**
A: `target/` is Maven's generated build output folder, already excluded
by `.gitignore` by default — it's never committed regardless of what's
in it.

---

## Activity Service — Document (MongoDB) vs Entity (JPA)

**Q: Why no `@GeneratedValue` for MongoDB's `id`?**
A: Postgres auto-increment needs one central authority counting 1, 2, 3...
MongoDB instead self-generates a unique `ObjectId` (timestamp + machine +
random bits) with zero coordination needed — no counting mechanism to
configure, so `id` is just a plain `String` MongoDB fills in itself.

**Q: Can two documents in the same MongoDB collection have different
fields without error?**
A: Yes, no error. Postgres = fixed paper form, every row has identical
columns. MongoDB = blank index cards, every document can have its own
fields even in the same collection. This is what "flexible schema" means
— why Mongo fits data that naturally varies (e.g. different activity
types tracking different extra metrics).

**One-line core distinction:** Postgres forces every row to match one
fixed structure; MongoDB lets every document define its own structure.
(Revisit if still fuzzy — this one didn't fully land on first pass.)

---

## Activity Service — Synchronous Call to User Service (RestClient)

**Q: What actual HTTP request does `UserValidationService.validateUser()`
send?**
A: `GET http://localhost:8081/api/users/{id}` — base URL from
`application.yml`'s `user-service.url` (via `@Value`), path from
`.uri("/api/users/{id}", userId)`. Lands on the real running
`UserController.getUserById()` in User Service.

**Q: Why catch `HttpClientErrorException.NotFound` but originally miss
the "service completely down" case?**
A: `.NotFound` only fires when User Service responds with a real 404 —
i.e. it's up and says "no such user." If User Service isn't running at
all, the connection itself fails with `ResourceAccessException` instead
— a different exception type our original catch block didn't handle, so
it went uncaught and crashed the request.

**Q: Why not just return `false` for both "confirmed missing" and
"couldn't check" cases?**
A: `false` should mean "verified: this user doesn't exist." If User
Service is just unreachable, we don't actually know — returning `false`
anyway would falsely tell real users their account doesn't exist during
an outage. Fixed by re-throwing a distinct `IllegalStateException` for
the unreachable case instead, later mapped to 503 (vs 404 for a
genuinely confirmed-missing user).

**Q: Full request trace for POST /api/activities (client → saved
document)?**
A: Controller (`@Valid` triggers) → `ActivityService.createActivity()` →
`UserValidationService.validateUser()` sends a real GET to User Service
→ User Service's own Controller→Service→Repository→Postgres chain runs
→ response (200 or 404) returns to Activity Service → if valid, build
`Activity`, `activityRepository.save()` → MongoDB assigns ObjectId,
auto-creates `fitness_activity_db` on first write → `toResponse()` →
back through Controller → client.

---

## MongoDB Document vs JPA Entity

**Q: Why no `@GeneratedValue` for MongoDB's `id`?**
A: MongoDB self-generates a unique `ObjectId` (timestamp + machine +
random bits), no central counting needed — unlike Postgres
auto-increment, which needs one authority handing out sequential
numbers. `id` is just `String`, MongoDB fills it in itself.

**Q: Can two documents in the same collection have different fields?**
A: Yes, no error — that's what "flexible schema" means. Postgres = every
row must match one fixed set of columns. MongoDB = every document can
have its own fields, even in the same collection.

---

## Docker Desktop restart / container persistence (live-verified)

**Q: After a Docker Desktop restart, was container data still there?**
A: Yes — `docker ps -a` showed the same container ID as before ("Exited"
status), and `docker start mongodb` resumed the *same* container, not a
fresh one. Confirms: stopping the app/Docker Desktop ≠ losing data;
only deleting the container would.

---

## Global Exception Handling (`@RestControllerAdvice`)

**Q: Why does each service need its own `@RestControllerAdvice`, not one
shared one?**
A: Direct consequence of Concept 1 — microservices are separate
codebases/JARs/processes with no shared runtime. Each service is fully
self-contained; the only connection between them is network calls, so
each must define its own error handling.

**Q: Why create custom exception types (`UserNotFoundException`,
`EmailAlreadyExistsException`) instead of throwing generic
`IllegalArgumentException` everywhere?**
A: A generic exception carries no meaning about *what kind* of business
error occurred, so Spring can't map it to a specific status code —
everything falls back to a generic 500. Custom, distinctly-typed
exceptions let `@ExceptionHandler` match on type and return the correct
code: 404 (not found), 409 (already exists), 503 (dependency
unreachable) — verified live: `userId: "99"` went from 500 to a clean
404 once this was wired up.

**Q: Deliberate scope decision — why no DELETE endpoints for User/
Activity?**
A: Never used anywhere in the actual app flow (login → view activities
→ add activity → view AI recommendation) or in the course transcript.
Skipped deliberately to stay focused on the core flow given the time
budget — a scoping choice, not a gap, and worth stating as such in an
interview.

---

## Eureka — Service Discovery

**Q: Eureka Server vs Eureka Client — what's the actual distinction?**
A: Eureka Server = a separate Spring Boot app whose only job is tracking
"who's registered, and where" (the registry itself). Eureka Client = a
dependency added to every OTHER service that makes them register with
the server on startup and lets them look other services up. Common
interview trip-up: Eureka isn't "one thing" — there's a registry and
there are clients.

**Q: Why does Eureka Server need `register-with-eureka: false` /
`fetch-registry: false`?**
A: The Eureka Server dependency bundles Eureka Client behavior too —
without this, the server would try to register with (and fetch from)
itself, which is circular and pointless. These flags say "you're the
registry, don't also act like a client of yourself."

**Q: Does having `spring.application.name: eureka-server` mean Eureka
Server appears in its own registry?**
A: No — having a name and registering are separate steps.
`register-with-eureka: false` blocks the registration step regardless
of the app having a name.

**Q: What's `@LoadBalanced` on a `RestClient.Builder` actually do?**
A: Marks that builder so any request built from it resolves the "host"
as a Eureka service NAME (e.g. `http://user-service`) instead of a real
address — Spring intercepts the call, asks Eureka where that service
actually is, and swaps in the real address transparently.

**Q: If Eureka Server goes down, does Activity Service → User Service
communication still work?**
A: No. `http://user-service` isn't a real address — something (Eureka)
has to translate the name into a real one at request time. No registry
reachable → no translation → the call fails, even if User Service
itself is running fine. This is a real tradeoff: removed the hardcoded-
URL fragility, introduced a new dependency on the registry being up
(mitigated in production with a Eureka cluster, not just one instance).

**Q: Live-verified bug — `@LoadBalanced RestClient.Builder` +
Eureka Client together crashed Activity Service on startup
(`BeanCurrentlyInCreationException` / circular reference on
`scopedTarget.eurekaClient`). What was the actual cause?**
A: Confirmed via [spring-cloud-netflix#4382](https://github.com/spring-cloud/spring-cloud-netflix/issues/4382):
newer Spring Cloud versions have Eureka's OWN internal HTTP client also
built on `RestClient`. If the app defines its own `@LoadBalanced
RestClient.Builder` bean, Spring's internal Eureka registration code can
end up wired to THAT bean instead of a plain one — creating a circular
dependency (the load balancer needs Eureka to resolve names, but Eureka
registration needs the load-balanced client to register). User Service
never hit this because it never defines a `@LoadBalanced` bean — only
services that BOTH register with Eureka AND make outbound load-balanced
calls are exposed to it.

**Q: How was it fixed?**
A: Replaced `@LoadBalanced RestClient.Builder` with a direct
`DiscoveryClient` lookup: explicitly call
`discoveryClient.getInstances("user-service")`, take the first
instance's real URI, and build a plain (non-load-balanced) `RestClient`
against it manually. Sidesteps the collision entirely since nothing is
marked `@LoadBalanced` anymore — more manual, but avoids the known
framework bug. A legitimate, production-valid alternative pattern, not
a hack.

**Q: What's the actual debugging lesson here?**
A: When a stack trace shows a circular/currently-in-creation bean
exception right after adding a new bean, suspect a framework-level
wiring collision, not necessarily your own logic. Verify against the
framework's own issue tracker before guessing at config fixes — don't
apply unverified property names (`eureka.client.restclient.enabled`
was tried and was wrong; the IDE's "unknown property" warning caught it
immediately, which is a good signal to stop and verify rather than
keep guessing).

---

## API Gateway (scaffolded, config written, not yet tested)

**Q: Reactive vs Servlet-based Gateway — which did we pick and why?**
A: Reactive Gateway (`spring-cloud-starter-gateway-server-webflux`, built
on WebFlux) — the traditional, long-established, well-documented Spring
Cloud Gateway. A newer non-reactive "Gateway" variant now also exists on
Initializr, but is less mature/documented and less likely to match
standard tutorials.

**Q: Trace `GET http://localhost:8080/api/users/1` through the Gateway.**
A: Request hits Gateway (8080) -> matches route predicate `Path=/api/users/**`
-> that route's `uri: lb://user-service` triggers a Eureka lookup for
`user-service` -> Eureka returns the real address (`localhost:8081`) ->
Gateway forwards the same path to that real address -> User Service's
normal Controller/Service/Repository chain runs, unaware it went through
a Gateway -> response flows back through the Gateway to the client.

**Q: Why does the Gateway still need the Eureka Client dependency even
though `lb://user-service` is already written in config?**
A: `lb://` is just config text saying "resolve this via load balancing."
It doesn't by itself know how to resolve anything — that resolution
requires an active Eureka registry connection, which only exists because
of the Eureka Client dependency. Without it, `lb://user-service` has no
mechanism behind it and requests would fail.

**Q: What does `discovery.locator.enabled: false` do, and why set it?**
A: Spring Cloud Gateway can auto-generate routes from whatever's
registered in Eureka, with no explicit rules. We disabled that and wrote
explicit `routes:` entries instead (`Path=/api/users/**` ->
`lb://user-service`, `Path=/api/activities/**` -> `lb://activity-service`)
— more predictable and matches the transcript's explicit routing table.

Status: VERIFIED working end to end. `GET http://localhost:8080/api/users/1`
and `POST http://localhost:8080/api/activities` both succeeded routed
through the Gateway — client -> Gateway (8080) -> Eureka-resolved route ->
correct microservice -> correct database, for both services. Full
architecture (User Service, Activity Service, Eureka, Gateway) now
confirmed working together.

---

## Config Server (conceptual only — not built)

**Q: What is Spring Cloud Config Server and why use it?**
A: A separate small app whose only job is serving configuration to other
services (typically backed by a Git repo), instead of each service
keeping its own local `application.yml` in isolation. Centralizes shared
config (DB URLs, Eureka address, etc.) so changes happen in one place,
version-controlled, instead of drifting across N separate files.

**Q: Where does it sit in the architecture?**
A: Off to the side like Eureka — not on the direct request path, but a
dependency every other service needs at startup to fetch its config.

**Decision**: deliberately skipped hands-on implementation given the time
budget — the practical benefit is small at 4-service scale, and the
concept (fetch-config-at-startup from a central source) is the same
pattern already demonstrated hands-on with Eureka. Answer for an
interview: "centralize config with a Config Server backed by Git — one
source of truth, environment-specific overrides, no drift, no rebuild
needed to change config."

---

## Kafka vs Traditional Message Queues

**Q: Core difference between Kafka and something like RabbitMQ?**
A: Log-based vs queue-based. A traditional queue removes a message once
read by one consumer. Kafka keeps messages in an ordered, append-only
log for a retention period — multiple independent consumers (or
consumer groups) can each read the same messages at their own pace
without affecting each other. Kafka is really a distributed log/
streaming platform usable as a queue, not purely a queue itself.

**Q: Do we need Kafka between Activity Service and User Service too?**
A: No. Kafka is fire-and-forget with no built-in immediate response —
wrong tool when the caller needs a definite answer before proceeding
(user validation). Rule: sync (REST) when the caller must block for an
answer; async (Kafka) when the work can happen later without blocking
the caller. Confirmed by the transcript's own framing (Part 2 §25).

---

## Kafka Producer/Consumer — Live Build

**Q: Why a separate `ActivityEvent` class instead of publishing the
`Activity` document directly to Kafka?**
A: Same DTO-separation principle as `User` vs `UserResponse`. Publishing
the raw `Activity` would leak Activity Service's internal storage shape
as a public contract — any future DB-only field change would silently
change what AI Service receives. `ActivityEvent` is a deliberately
minimal, intentional contract with only what AI Service actually needs.

**Q: Why does `ActivityEvent` exist as two separate, near-identical
classes (one per service) instead of one shared class?**
A: Real, deliberate tradeoff — no shared Java module between
independently-deployable services, since a shared library would
reintroduce coupling (both services would need redeploying together
when it changes). Verified live: this is exactly what caused a real bug
(see below) — worth knowing the cost, not just the theory.

**Q: What does `@KafkaListener` actually do, and how is it different
from a Controller method being called?**
A: Runs the annotated method automatically whenever a new message
arrives on the topic — invoked by Spring's Kafka listener machinery in
a background thread, not by an external caller expecting a response.
Fire-and-forget from Kafka's side: nothing waits on `consume()`'s
return value. For N published messages, `consume()` runs N times (no
batching by default).

**Q: Consumer groups — what does `group-id` actually control?**
A: If multiple instances of a service share the same `group-id`, Kafka
splits messages between them (load-shared, each message processed
once). Different `group-id`s = each group independently receives and
processes every message in full.

---

## Two Real Bugs Fixed Building the Kafka Pipeline (live-verified)

**Bug 1 — `spring-kafka` vs `spring-boot-starter-kafka`**
A: activity-service's `pom.xml` used the raw `spring-kafka` library
instead of the `spring-boot-starter-kafka` starter. The jar was
genuinely on the classpath, but `KafkaTemplate` still wasn't
auto-created — `UnsatisfiedDependencyException: No qualifying bean of
type 'KafkaTemplate'`. Root cause: Spring Boot's auto-configuration
(the mechanism that reads `application.yml` and creates beans like
`KafkaTemplate` automatically) is tied to the starter dependency, not
just the underlying library being present. **Lesson: always prefer the
`spring-boot-starter-*` variant over the raw library** — starters are
what plug into Spring Boot's "just works" auto-configuration model.
(Ruled out stale build/cache first: deleted `target/`, did a clean
Rebuild Project, same error persisted — confirmed it was a real
dependency-choice bug, not a caching artifact.)

**Bug 2 — cross-service class-name mismatch breaking deserialization**
A: `JsonDeserializer` defaults to trusting a class-name "stamp" the
producer embeds in each message's metadata, then tries to load a class
with that *exact* name on the consumer side. Since activity-service's
`ActivityEvent` and ai-service's `ActivityEvent` are separate classes
in different packages (see above), AI Service tried to load
`com.fitness.activityservice.event.ActivityEvent` — which doesn't
exist in its own codebase — and threw `ClassNotFoundException` /
`RecordDeserializationException` on every message. Fixed with two
consumer properties: `spring.json.use.type.headers: false` (ignore the
producer's embedded class name entirely) +
`spring.json.value.default.type: com.fitness.aiservice.event.ActivityEvent`
(always decode into this specific local class instead). Live-verified:
messages that failed earlier and stayed in the Kafka log were
successfully reprocessed once AI Service restarted with the fix —
concrete proof Kafka doesn't drop unconsumed/failed messages the way a
traditional "remove on read" queue might.

---

## Gemini Integration — AI Service

**Q: What is "context" in the context of an LLM call?**
A: All the information given to the model alongside the instruction, since
the model has no memory of your app/data between calls — every call is a
blank slate. Here, `ActivityEvent`'s fields (type, duration, calories) ARE
the context; the fixed instruction text stays the same every call.

**Q: Why does `GeminiRequest`/`GeminiResponse` exist as typed DTOs instead
of raw `Map`/`JsonNode`?**
A: Same DTO-at-every-boundary rule already applied everywhere else in this
project (Entity<->API, Entity<->Kafka event). Gemini's API is just another
boundary — typed classes catch shape mistakes at compile time and are
self-documenting; raw Maps fail silently/at runtime on typos.

**Q: Why are `Content`/`Part` nested static classes inside `GeminiRequest`/
`GeminiResponse` instead of their own top-level files?**
A: They only exist to mirror Gemini's JSON shape and have no meaning
outside their parent DTO — nesting makes that relationship explicit
instead of cluttering the `dto` package with tiny throwaway classes.

**Q: Why does `GeminiService`'s constructor have to be written manually
instead of using `@RequiredArgsConstructor`?**
A: `@RequiredArgsConstructor` can't add per-parameter annotations like
`@Value`. Since `apiUrl`/`apiKey` are plain `String`s that need
`@Value("${gemini.api.key}")` to know where to come from, the constructor
must be written by hand. Rule: `@RequiredArgsConstructor` for plain bean
injection; manual constructor once any parameter needs `@Value`,
`@Qualifier`, etc. (`RecommendationService` has no `@Value` params, so it
DOES use `@RequiredArgsConstructor` — same rule, opposite outcome.)

**Q: Why introduce `RecommendationService` instead of having
`ActivityEventConsumer` call `GeminiService` + `RecommendationRepository`
directly?**
A: Same layering already used in Activity Service: `ActivityController`
doesn't touch `ActivityRepository`/`UserValidationService`/
`ActivityEventProducer` directly — it calls one thing,
`ActivityService.createActivity()`, which orchestrates. Consumer/Controller
= thin entry point; Service = orchestration logic. Keeps
`ActivityEventConsumer` doing exactly one job: receive from Kafka, delegate.

**Q: If `application-local.yml` defines a key (`gemini.api.key`) that
doesn't exist anywhere in the base `application.yml`, does that work?**
A: Yes. Spring doesn't override key-by-key requiring prior existence in
the base file — it merges all active property sources into one combined
map. A profile-specific file can introduce brand-new keys from scratch;
nothing needs to "already be there" to be added.

**Q: What is a Dead Letter Topic (DLT), and do we have one?**
A: A separate Kafka topic that permanently-failing messages get moved to
after retries are exhausted, instead of being silently dropped after
just a log line. We do NOT have one configured yet — known, stated gap,
not a bug. Currently: a message that keeps failing (e.g. bad Gemini API
key) retries a few times via Spring Kafka's default error handler, then
is logged and effectively lost. The app itself does not crash — Kafka
consumer failures are isolated per-message, not fatal to the service.

---

## Live Bug — Eureka Hostname Resolution (`Priyam.mshome.net`)

**Q: What went wrong?**
A: Every service registers with Eureka using its machine's network hostname
by default — on Windows this can be a local-network name like
`Priyam.mshome.net`, not a real resolvable address. When API Gateway tried
to route a request to Activity Service via `lb://activity-service`, Eureka
handed back that hostname, and Gateway's connection attempt failed with
`UnknownHostException` — the hostname isn't resolvable outside the local
machine's own network naming.

**Q: Why did other services (e.g. User Service) seem unaffected?**
A: They weren't actually immune — the bug only shows up on calls that go
*through* Eureka's registry lookup (like Gateway resolving `lb://...`, or
one service discovering another via `DiscoveryClient`). Direct calls
straight to a service's own port (e.g. Postman hitting `localhost:8081`
directly) never touch Eureka's registered hostname at all, so those always
worked regardless. Activity Service was simply the first place this
particular path got tested.

**Q: How was it fixed, and why does it work?**
A: Added `eureka.instance.prefer-ip-address: true` to every service
(User, Activity, AI, Gateway — not Eureka Server itself, which never
registers as a client). This tells each service to register its real IP
address with Eureka instead of its hostname. IP addresses need no DNS
resolution step to connect to, so the lookup can't fail the same way.

**Q: Debugging lesson?**
A: A fast (~10ms) 500 with an empty/generic error body is a strong signal
the request never reached the target service's own code at all — check
the layer *in front of* the target (here, the Gateway) before assuming the
bug is in the destination service's logic. Confirmed by tracing: Eureka's
dashboard showed all 4 services correctly registered (ruling out a
registration failure) — the actual failure was one specific step later,
at final TCP connection time, not at service-discovery lookup time.

---

## Live Bug — Silent Wrong-Database Writes (`spring.data.mongodb.uri` deprecated)

**Q: What went wrong?**
A: Both Activity Service and AI Service used `spring.data.mongodb.uri` to
set the Mongo connection string, including the target database name
(`fitness_activity_db` / `fitness_ai_db`). In Spring Boot 4.1.1, this
property is deprecated in favor of `spring.mongodb.uri` (one level less
nested — no `data:` prefix). The IDE flagged this as a deprecation warning
early on, but it was dismissed as non-urgent at the time.

**Q: What did the deprecated property actually do — did it fail loudly?**
A: No — that was the dangerous part. It connected successfully (no error,
no crash) but silently ignored the database name from the URI, falling
back to MongoDB's own default database, `test`. Every save appeared to
work fine in the application logs (`"Saved recommendation for activity
..."` printed correctly), but the data was landing in the wrong database
the entire time.

**Q: How was it caught?**
A: Verified successful Kafka->Gemini->save flow via console logs, then
independently queried MongoDB directly (`fitness_ai_db.recommendations`)
to confirm the save — found it empty despite the success log. Checked
`admin.listDatabases` and found `fitness_ai_db` didn't exist as a
database at all; only Mongo's default `test` database did, and it held
the missing documents. Cross-checked Activity Service the same way and
found the identical bug had been present since the service was first
built — 12 activities silently saved to `test` instead of
`fitness_activity_db`.

**Q: The fix, and the lesson?**
A: Changed `spring.data.mongodb.uri` -> `spring.mongodb.uri` in both
services' `application.yml`. Lesson: a deprecation warning that doesn't
outright break startup can still cause a silent correctness bug — the
app "working" (no exceptions, no crashes, success logs) is not the same
as the app being *correct*. Success logs only prove the code path ran
without throwing; they don't prove the data landed where intended.
Independently verifying state in the actual database (not just trusting
application logs) is what caught this.

---

## Swapping AI Providers — Gemini to Grok Attempt (live-verified, then reverted)

**Q: This directly answers the earlier deferred question — why would AI
Service benefit from an interface + multiple implementations for its AI
provider?**
A: Confirmed hands-on: swapping Gemini for Grok only required changing
`GeminiService` (URL, auth header, request/response DTOs to match Grok's
OpenAI-style `messages`/`choices` shape) and the one line in
`RecommendationService` that references it. Everything else — the
`Recommendation` model, `RecommendationRepository`,
`ActivityEventConsumer`, the Kafka pipeline — needed zero changes,
because they only ever depended on one method:
`getRecommendation(ActivityEvent)`. An interface (`AiProvider`) would
formalize this further, but even without one, the existing layering
already isolated the blast radius of a provider swap to a single class.

**Q: Why did the Grok attempt fail, and was it a code bug?**
A: No — the code was correct end-to-end. Request reached Grok's API,
authenticated successfully (`403`, not `401` — proves the API key was
valid), and failed only because xAI requires purchased credits/billing
on the account before any request succeeds, even a test call. Unlike
Gemini, there's no free tier. Reverted to Gemini (which does have a free
tier) to keep the project runnable without spending money; Grok's
integration code was removed rather than left dead/unused in the
codebase (`GeminiService`/`GeminiRequest`/`GeminiResponse` restored,
`GrokService`/`GrokRequest`/`GrokResponse` deleted).

**Q: What's the interview-ready way to talk about this?**
A: "I actually built and tested a provider swap (Gemini to Grok) to
validate the architecture supports it — confirmed the auth and request
flow worked correctly, but xAI requires paid credits with no free tier,
so I reverted to Gemini for a runnable demo. The swap itself only
touched one service class and its DTOs; nothing else in the pipeline
needed to change." Real, hands-on evidence beats describing the pattern
hypothetically.

---

## File-by-File Reference (one-line purpose per file)

### User Service
- `UserServiceApplication.java` — starts the app, tells Spring where to scan for components
- `model/User.java` — maps a Java object to a row in the `users` PostgreSQL table
- `repository/UserRepository.java` — lets you save/find users without writing SQL
- `dto/RegisterRequest.java` — shape of the JSON a client sends to register
- `dto/UserResponse.java` — shape of the JSON sent back (no password)
- `service/UserService.java` — actual business logic: register, get by id, get all
- `controller/UserController.java` — defines the HTTP endpoints (`/api/users/...`)
- `exception/UserNotFoundException.java` — thrown when a user id doesn't exist
- `exception/EmailAlreadyExistsException.java` — thrown when registering a duplicate email
- `exception/GlobalExceptionHandler.java` — turns exceptions into proper HTTP error responses

### Activity Service
- `ActivityServiceApplication.java` — starts the app
- `model/Activity.java` — maps a Java object to a document in MongoDB
- `repository/ActivityRepository.java` — save/find activities in MongoDB
- `dto/ActivityRequest.java` — shape of incoming "log an activity" JSON
- `dto/ActivityResponse.java` — shape of the response sent back
- `service/ActivityService.java` — validates user, saves activity, fires Kafka event
- `service/UserValidationService.java` — checks with User Service that a userId is real
- `controller/ActivityController.java` — defines the HTTP endpoints
- `config/RestClientConfig.java` — provides the HTTP client used to call User Service
- `event/ActivityEvent.java` — shape of the message sent to Kafka
- `event/ActivityEventProducer.java` — actually sends that message to Kafka
- `exception/UserNotFoundException.java` — thrown if the user doesn't exist
- `exception/GlobalExceptionHandler.java` — turns exceptions into HTTP error responses

### Eureka Server
- `EurekaServerApplication.java` — runs the service registry every other service registers with

### API Gateway
- `ApiGatewayApplication.java` — starts the single entry point that routes requests to the right service (routing rules live in `application.yml`)
- `config/SecurityConfig.java` — requires a valid JWT on every request except `/api/users/register`; validates tokens against Keycloak

### AI Service
- `AiServiceApplication.java` — starts the app
- `model/Recommendation.java` — maps a Java object to a document in MongoDB
- `repository/RecommendationRepository.java` — save/find recommendations in MongoDB
- `event/ActivityEvent.java` — shape of the message received from Kafka (separate copy from Activity Service's)
- `event/ActivityEventConsumer.java` — listens for new Kafka messages and reacts
- `service/RecommendationService.java` — coordinates calling Gemini and saving the result
- `service/GeminiService.java` — actually talks to Gemini's API
- `dto/GeminiRequest.java` — shape of what we send to Gemini
- `dto/GeminiResponse.java` — shape of what Gemini sends back
- `config/RestClientConfig.java` — provides the HTTP client used to call Gemini

---

## Keycloak — Authentication at the Gateway

**Q: What problem does this solve that we didn't have before?**
A: Before this, the Gateway forwarded every request to every service with
zero identity checking — anyone could call `/api/activities` as any
`userId` they typed in, with no proof of who they actually were. Keycloak
adds a real identity layer: a client must present a cryptographically
signed token proving who the request is acting on behalf of before the
Gateway lets it through.

**Q: What is a realm, a client, and a user — and how do they relate?**
A: A **realm** (`fitness-app`) is an isolated tenant inside Keycloak — its
own users, clients, and tokens, walled off from other realms (e.g.
Keycloak's own internal `master` realm). A **client** (`fitness-app-client`)
represents the *application* asking for tokens — our API Gateway. A
**user** (`tester2`) is the actual person logging in. Getting a token
requires proving both: the client is legitimate (client ID + client
secret) and the user is legitimate (username + password). Analogy: a
security desk issuing a visitor badge — the user is the visitor, the
client is the department vouching for/requesting the badge on their
behalf.

**Q: Public vs confidential client — which did we use and why?**
A: Confidential (Client authentication = On). A public client (e.g. a
browser SPA) can't safely hold a secret since its code is visible to
anyone. A confidential client is a backend/server component that can
securely store a secret — that's the Gateway. We also enabled **Direct
access grants**, which allows fetching a token with a raw username+password
POST (no browser redirect) — useful for testing via Postman before wiring
up a real login UI.

**Q: What actually happens when a client requests a token?**
A: POST to Keycloak's token endpoint
(`/realms/fitness-app/protocol/openid-connect/token`) with `grant_type`,
`client_id`, `client_secret`, `username`, `password` as
`x-www-form-urlencoded` body fields. Keycloak checks the client secret
(proves the caller really is `fitness-app-client`) and the user's
credentials (proves it's really that user), then returns a signed JWT
**access_token** (short-lived, 5 min here) plus a **refresh_token**
(longer-lived, used to get a new access token without re-entering a
password).

**Q: Is a JWT encrypted?**
A: No — signed, not encrypted. The payload (header.payload.signature) is
plain base64, readable by anyone who intercepts it (confirmed by decoding
a real token at jwt.io and seeing `preferred_username`, `email`, `exp`,
`iss` in plain text). What prevents forgery is the **signature**: Keycloak
signs with a private key only it holds; the Gateway verifies using
Keycloak's public key. Anyone can read a JWT's claims, but nobody except
Keycloak can produce a signature that verifies correctly.

**Q: How does the Gateway verify a token without calling Keycloak on every
single request?**
A: `spring-boot-starter-oauth2-resource-server` + one config value —
`issuer-uri: http://localhost:8180/realms/fitness-app`. On startup, Spring
calls that URL, discovers Keycloak's public key endpoint, and caches the
keys. After that, verifying a token's signature is a local cryptographic
check — no network call to Keycloak per request.

**Q: Why does adding the resource-server dependency break registration?**
A: The moment that dependency is on the classpath, Spring Security
auto-locks every endpoint by default, requiring a valid token for all of
them — including `/api/users/register`. But a brand-new user has no
account yet, so they can't have a token yet either. Fixed with an explicit
`SecurityConfig`: `permitAll()` for `/api/users/register`,
`authenticated()` for everything else.

**Q: Why `SecurityWebFilterChain` instead of the more commonly-documented
`SecurityFilterChain`?**
A: The Gateway is built on Spring Cloud Gateway's WebFlux
(reactive/non-blocking) stack, not a traditional servlet stack.
`@EnableWebFluxSecurity` + `SecurityWebFilterChain` is the reactive-stack
equivalent — using the servlet-based types here wouldn't compile/wire
correctly against a WebFlux app.

**Q: Why disable CSRF protection in SecurityConfig?**
A: CSRF protection defends cookie/session-based browser apps. This
Gateway is a stateless API authenticated via a `Bearer` token in a
header, not cookies — CSRF doesn't apply, and leaving it on would
actually block legitimate API calls (Postman, the real frontend) that
don't carry a CSRF token.

### Live Bug — "Account is not fully set up" (invalid_grant)

**What went wrong:** Requesting a token via Postman for a freshly created
Keycloak user consistently failed with `400 invalid_grant: "Account is
not fully set up"` — even after resetting the password, confirming
"Temporary" was off, checking the user's "Required user actions" field
was empty, and deleting/recreating the user entirely from scratch.

**How it was actually diagnosed:** Rather than keep guessing at the
direct-grant API path, tested the *same* username/password through
Keycloak's own browser login page
(`/realms/fitness-app/account`). That immediately revealed the real
cause: Keycloak's realm-level **User Profile** config marks `email`,
`firstName`, `lastName` as required fields. A user missing them is
blocked from completing login — the direct-grant (password) flow surfaces
this as the generic `invalid_grant` / "Account is not fully set up"
error instead of a clearer message, because the password grant can't
redirect to a "complete your profile" form the way the browser flow can.

**Fix:** Filled in email/first name/last name for the user via the forced
"Update Account Information" screen in the browser flow. Afterward, the
exact same Postman request (same client, same password) succeeded and
returned a real access/refresh token.

**Lesson:** When an API-level auth error is vague, test the same
credentials through the provider's own UI/browser flow if one exists —
it often surfaces the real validation failure with a human-readable
message that the API path only returns as a generic error code.

---

## React Frontend — Connecting the UI to the Backend

**Q: Registration creates a Postgres row via User Service — does that
automatically create a Keycloak login too?**
A: No, not by default. User Service's `/register` and Keycloak are two
separate systems. Fixed by adding `KeycloakUserService` to User Service:
after saving the Postgres row, it fetches a Keycloak **admin token**
(`admin-cli` client, `master` realm) and calls Keycloak's **Admin REST
API** (`POST /admin/realms/fitness-app/users`) to create a matching
Keycloak user with the same email/password. The admin call happens
server-side in User Service, never in the browser — admin credentials
must never reach frontend JS.

**Q: What ID does the frontend use to identify "the current user," and
why did that break Activity Service?**
A: After login, the frontend only has the JWT's `sub` claim — Keycloak's
UUID. But Activity Service's `UserValidationService` was calling
`GET /api/users/{id}` with a `Long` (User Service's own Postgres
auto-increment ID). Sending a UUID into a `Long` parameter caused a 400.
Fixed by adding a `keycloakId` column to the `User` entity (populated
from the `Location` header Keycloak returns on user creation), a
`findByKeycloakId` repository method, and a new endpoint
`GET /api/users/by-keycloak-id/{keycloakId}` that `UserValidationService`
calls instead. This makes Keycloak's UUID the one identity that flows
through every service — the standard pattern when an external IdP is
involved: each service's own primary key stays internal, but a
foreign-key-style column links it back to the IdP's ID.

**Q: Why did a already-correct `permitAll()` rule on `/api/users/register`
still return 401 from the browser, while curl and Postman succeeded?**
A: A stale `access_token` was sitting in `localStorage` from an earlier
login, and the axios interceptor attached it to every request — including
registration, which must stay anonymous. An expired/invalid
`Authorization` header on an OAuth2-resource-server-protected app can
cause Spring Security to reject the request before `permitAll()` is even
evaluated, depending on filter ordering. curl/Postman never sent that
header, so they never hit it. Fixed by excluding `/api/users/register`
specifically in the interceptor, so it's never sent a token regardless of
what's in storage.

**Q: `GET /api/activities` showed every user's activities to every logged-
in user — why, and how was it fixed?**
A: The endpoint was built before login/auth existed, so it was written as
"return everything" with no concept of "the current user." Nobody updated
it once real users and the frontend arrived. Fixed by adding
`findByUserId` to `ActivityRepository`, a `getActivitiesByUserId` service
method, and a new `GET /api/activities/user/{userId}` endpoint — the
Dashboard now calls that instead, passing the JWT's `sub` claim.

**Q: Why did a write (`POST /api/activities`) keep succeeding throughout
all of this, while reads (`GET`) kept breaking?**
A: Two unrelated bugs, both on the read side only — the missing
`userId` filter, then briefly a stale service process that hadn't picked
up the new route. The POST path was never touched by either bug, so every
activity was saved to MongoDB correctly and immediately, the entire time.
Interview-ready framing: "writes succeeded and persisted the whole time;
it was the read path that had bugs" — a meaningfully different, less
severe class of failure than actual data loss.

**Q: Where does the AI recommendation actually come from in the UI, and
why didn't it show up immediately?**
A: AI Service was never wired to any HTTP endpoint before — it only
consumed Kafka events and saved `Recommendation` documents to MongoDB.
Added a `RecommendationController` (`GET
/api/recommendations/activity/{activityId}`, 404 if not generated yet)
and a new Gateway route (`/api/recommendations/** -> ai-service`, missing
until now). `ActivityItem.jsx` independently fetches its own
recommendation per activity and silently shows nothing on 404, since "not
generated yet" is a normal, expected state — Gemini's response arrives
asynchronously, seconds after the activity itself is created.

---

## Open / Not Yet Answered

- Add a Dead Letter Topic for AI Service's Kafka consumer — noted gap,
  not yet implemented.
- Markdown from Gemini's response renders as raw text (`###`, `**bold**`)
  in the Dashboard instead of formatted output — not yet fixed.
- `/api/activities` (the original, unfiltered endpoint) still exists
  alongside the new `/api/activities/user/{userId}` one — kept rather
  than removed, in case anything else depends on it, but nothing in the
  frontend uses it anymore.
