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

## Open / Not Yet Answered

- Why might AI Service specifically benefit from an interface +
  multiple implementations later (e.g. swapping Gemini for another
  model)? — seeded, not yet resolved.
