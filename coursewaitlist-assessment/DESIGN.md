# Course Waitlist - Design Specification

## 1. Scope

The service supports one course offering:
- Enroll a learner.
- Reject learners who have not satisfied prerequisites.
- Add eligible learners to a FIFO waitlist when full.
- Drop an enrolled learner.
- Promote the earliest eligible waitlisted learner.

No frontend, payment, identity, course catalog, or database is included.

## 2. Data structures

- Map<String, EnrollmentStatus>: one registration status per learner.
- Deque<String>: FIFO waitlist.
- Map<String, Boolean>: current prerequisite eligibility for waitlisted learners.

## 3. Business rules and assumptions

1. A learner can create only one registration record per offering.
2. A learner who drops cannot register again for that offering.
3. A prerequisite failure does not create a registration record.
   The learner may retry after satisfying the prerequisite.
4. Only eligible learners can enroll or join the waitlist.
5. Eligibility is checked again when a learner reaches the front
   of the waitlist.
6. An ineligible learner is skipped and marked SKIPPED.
   Promotion continues to the next learner.
7. A waitlisted learner cannot drop because they do not hold a seat.
8. A repeated drop returns ALREADY_DROPPED.

## 4. Capacity invariant

0 <= enrolledCount <= capacity

The service derives enrolledCount from registration statuses.
It does not maintain a separate mutable seat counter.

## 5. FIFO promotion

When an enrolled learner drops:
1. Mark the learner DROPPED.
2. Remove the first learner from the waitlist.
3. Check current prerequisite eligibility.
4. If ineligible, mark SKIPPED and continue.
5. If eligible, mark ENROLLED and stop.

## 6. Concurrency

All state-changing service methods are synchronized.
This protects in-memory state within one service instance in one JVM.

This is not a distributed locking solution.

## 7. Limitations

- In-memory state is lost when the process stops.
- Synchronization does not coordinate multiple application instances.
- No persistent audit history is stored.
- Prerequisite eligibility is supplied by the caller.
- A production implementation would need database transactions,
  locking or optimistic concurrency, and durable registration records.