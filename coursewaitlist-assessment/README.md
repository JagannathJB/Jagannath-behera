# CourseWaitlist – Java 17 assessment solution

## Scope
One course offering only. Supports enrollment, dropping, FIFO waitlist promotion, prerequisite checks, duplicate protection, and focused tests. No frontend, database, identity provider, payments, or deployment.

## Assumptions and policy
- Capacity is a positive fixed integer.
- The caller supplies the prerequisite result (`true` means the learner completed the required prerequisite).
- A learner must satisfy the prerequisite to enroll or enter the waitlist.
- An eligible learner joins the tail of the FIFO queue when the course is full.
- Repeated requests while ENROLLED or WAITLISTED are idempotently rejected as `ALREADY_ACTIVE`; they do not create another queue entry.
- A successful drop immediately triggers promotion.
- The promotion loop skips stale queue entries and any ineligible queue head, marking an ineligible learner `SKIPPED`, then continues to the next eligible learner.
- The prerequisite result is supplied by the caller. `updatePrerequisiteEligibility` models a change received from the authoritative academic record while a learner is waiting; promotion re-checks the latest value. The assessment test exercises the case where a learner becomes ineligible while waiting.
- A dropped or skipped learner may submit a fresh request. Only one active state per learner is maintained.
- FIFO means order in which synchronized enrollment requests successfully append to the queue.

## Design
`CourseWaitlistService` is the service boundary. It owns:
- `statuses`: learner ID to current state.
- `waitlist`: FIFO deque of learner IDs.
- `capacity`: fixed maximum active enrollment count.

### Invariants
1. `0 <= enrolledCount <= capacity`.
2. A learner is never simultaneously enrolled and waitlisted.
3. A learner has at most one active enrollment/waitlist record.
4. Only eligible learners enter the waitlist.
5. A successful drop and its promotion are one synchronized operation.
6. A duplicate or invalid drop does not change state.

### Concurrency and persistence
All mutating operations are synchronized on the service instance. This makes the in-memory implementation atomic within one JVM. It does not coordinate multiple application instances and does not survive restart. In a database-backed service, use a transaction plus row-level locking (or optimistic versioning with retry) on the course offering, enforce a unique active enrollment constraint, and claim the earliest waitlist row using an ordering key and suitable locking (`FOR UPDATE SKIP LOCKED` where supported). Keep drop and promotion in the same transaction.

## Run
Requires JDK 17+ and Maven 3.9+.

```bash
mvn test
```

## API examples
```java
var service = new CourseWaitlistService(2);
service.enroll("learner-101", true); // ENROLLED
service.enroll("learner-102", true); // ENROLLED
service.enroll("learner-103", true); // WAITLISTED
service.drop("learner-101");         // promotes learner-103
```

## AI use and independent verification record
AI-assisted activity: requirement decomposition, edge-case identification, initial service/test design, and documentation structure.

Prompt used:
> Act as a senior Java backend engineer. Design a minimal Java 17 service for one course offering supporting enrollment, dropping, FIFO waitlist promotion, prerequisite checks, duplicate protection, invalid requests, and concurrency. State assumptions, invariants, and focused JUnit tests. Do not add a frontend or database.

AI suggestion summary and review:
- Use a FIFO deque: accepted; it directly represents insertion order.
- Check prerequisites before enrollment/waitlisting: accepted.
- Promote after drop: accepted; implemented in the same synchronized method.
- Use synchronization for the in-memory fallback: accepted with limitation documented (single JVM only).
- Add a configurable policy engine / Spring Boot / database: rejected as outside the bounded scope.
- Re-check prerequisite eligibility at promotion: adapted. The starter-free model stores the eligibility result at waitlist entry; production design should re-check the authoritative prerequisite source at promotion time. Because only eligible learners can enter this queue, the in-memory example cannot naturally create an ineligible queue head through the public API.

Verification evidence:
- Automated tests cover available capacity, full capacity, duplicate requests, FIFO promotion, prerequisite failure, invalid IDs, repeated drops, re-enrollment after drop, and concurrent enrollment capacity.
- Run `mvn test`; inspect the test report under `target/surefire-reports`.
- Review the state-changing methods: enrollment and drop/promotion are synchronized; capacity is derived from ENROLLED states rather than maintained as a separately mutable counter.

## Limitations / follow-up risks
- In-memory state is lost on restart and is not shared across JVMs.
- Prerequisite eligibility is supplied by the caller; this prototype models updates through `updatePrerequisiteEligibility`. A production service must integrate with and revalidate against the authoritative prerequisite source.
- No cancellation deadline, course-term model, notifications, audit history, or administrative override is implemented because these are outside scope.
- The simple status map stores the current state, not a full enrollment event history.
