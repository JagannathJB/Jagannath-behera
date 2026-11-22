
package com.example.coursewaitlist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Concurrency tests for CourseWaitlistService.
 *
 * Validates:
 * 1. Concurrent enrollment does not exceed capacity.
 * 2. Duplicate enrollment is prevented.
 * 3. Concurrent waitlist registration creates one entry.
 * 4. Concurrent drops promote a learner only once.
 * 5. Mixed concurrent operations preserve capacity and uniqueness.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CourseWaitlistServiceConcurrencyTest {

    private static final int THREADS = 32;

    /**
     * Executes tasks concurrently.
     * A latch releases all workers together to increase contention.
     */
    private static <T> List<T> runConcurrently(
            List<Callable<T>> tasks
    ) throws Exception {

        ExecutorService pool =
                Executors.newFixedThreadPool(tasks.size());

        CountDownLatch ready =
                new CountDownLatch(tasks.size());

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            List<Future<T>> futures = new ArrayList<>();

            for (Callable<T> task : tasks) {

                futures.add(pool.submit(() -> {

                    ready.countDown();

                    start.await();

                    return task.call();
                }));
            }

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Workers did not become ready in time"
            );

            start.countDown();

            List<T> results = new ArrayList<>();

            for (Future<T> future : futures) {
                results.add(future.get());
            }

            return results;

        } finally {
            pool.shutdownNow();

            assertTrue(
                    pool.awaitTermination(5, TimeUnit.SECONDS),
                    "Worker pool did not terminate"
            );
        }
    }

    /**
     * Concurrent learners compete for limited seats.
     */
    @RepeatedTest(20)
    void concurrentLearnersNeverExceedCourseCapacity()
            throws Exception {

        int capacity = 5;

        CourseWaitlistService service =
                new CourseWaitlistService(capacity);

        List<Callable<EnrollmentResult>> tasks =
                new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {

            String learnerId = "learner-" + i;

            tasks.add(() ->
                    service.enroll(learnerId, true)
            );
        }

        List<EnrollmentResult> results =
                runConcurrently(tasks);

        long enrolled = results.stream()
                .filter(result ->
                        result.outcome()
                                == EnrollmentResult.Outcome.ENROLLED)
                .count();

        long waitlisted = results.stream()
                .filter(result ->
                        result.outcome()
                                == EnrollmentResult.Outcome.WAITLISTED)
                .count();

        assertEquals(capacity, enrolled);

        assertEquals(
                THREADS - capacity,
                waitlisted
        );

        assertEquals(capacity, service.enrolledCount());

        assertEquals(
                THREADS - capacity,
                service.waitlistSnapshot().size()
        );

        assertTrue(
                service.enrolledCount() <= service.capacity()
        );

        assertUniqueWaitlist(service);
    }

    /**
     * The same learner cannot be enrolled more than once,
     * even when multiple requests arrive concurrently.
     */
    @RepeatedTest(20)
    void concurrentEnrollmentForSameLearnerCreatesOneRegistration()
            throws Exception {

        CourseWaitlistService service =
                new CourseWaitlistService(10);

        List<Callable<EnrollmentResult>> tasks =
                new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {

            tasks.add(() ->
                    service.enroll("same-learner", true)
            );
        }

        List<EnrollmentResult> results =
                runConcurrently(tasks);

        assertEquals(
                1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.ENROLLED
                )
        );

        assertEquals(
                THREADS - 1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.ALREADY_REGISTERED
                )
        );

        assertEquals(1, service.enrolledCount());

        assertEquals(
                EnrollmentStatus.ENROLLED,
                service.statusOf("same-learner")
        );
    }

    /**
     * Concurrent duplicate waitlist requests must create
     * exactly one waitlist entry.
     */
    @RepeatedTest(20)
    void concurrentWaitlistRequestsCreateOneWaitlistEntry()
            throws Exception {

        CourseWaitlistService service =
                new CourseWaitlistService(1);

        service.enroll("holder", true);

        List<Callable<EnrollmentResult>> tasks =
                new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {

            tasks.add(() ->
                    service.enroll("waitlisted-learner", true)
            );
        }

        List<EnrollmentResult> results =
                runConcurrently(tasks);

        assertEquals(
                1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.WAITLISTED
                )
        );

        assertEquals(
                THREADS - 1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.ALREADY_REGISTERED
                )
        );

        assertEquals(
                List.of("waitlisted-learner"),
                service.waitlistSnapshot()
        );

        assertEquals(
                EnrollmentStatus.WAITLISTED,
                service.statusOf("waitlisted-learner")
        );

        assertEquals(1, service.enrolledCount());

        assertUniqueWaitlist(service);
    }

    /**
     * Concurrent drop requests must release only one seat
     * and promote only one eligible waitlisted learner.
     */
    @RepeatedTest(20)
    void concurrentDropPromotesExactlyOneWaitlistedLearner()
            throws Exception {

        CourseWaitlistService service =
                new CourseWaitlistService(1);

        service.enroll("holder", true);

        for (int i = 0; i < 5; i++) {
            service.enroll("w" + i, true);
        }

        List<Callable<EnrollmentResult>> tasks =
                new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {

            tasks.add(() ->
                    service.drop("holder")
            );
        }

        List<EnrollmentResult> results =
                runConcurrently(tasks);

        assertEquals(
                1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.DROPPED
                )
        );

        assertEquals(
                THREADS - 1,
                countOutcome(
                        results,
                        EnrollmentResult.Outcome.ALREADY_DROPPED
                )
        );

        // FIFO: w0 must be promoted.
        assertEquals(
                EnrollmentStatus.ENROLLED,
                service.statusOf("w0")
        );

        assertEquals(
                EnrollmentStatus.DROPPED,
                service.statusOf("holder")
        );

        assertEquals(
                List.of("w1", "w2", "w3", "w4"),
                service.waitlistSnapshot()
        );

        assertEquals(1, service.enrolledCount());

        assertEquals(0, service.availableSeats());

        assertUniqueWaitlist(service);
    }

    /**
     * Mixed enrollment and drop operations should not
     * corrupt the course state or exceed capacity.
     */
    @RepeatedTest(10)
    void mixedConcurrentEnrollmentAndDropPreserveCapacity()
            throws Exception {

        int capacity = 4;

        CourseWaitlistService service =
                new CourseWaitlistService(capacity);

        List<Callable<Void>> tasks =
                new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {

            String learnerId = "learner-" + i;

            tasks.add(() -> {

                service.enroll(learnerId, true);

                if (learnerId.hashCode() % 2 == 0) {
                    service.drop(learnerId);
                }

                return null;
            });
        }

        runConcurrently(tasks);

        assertTrue(
                service.enrolledCount() <= capacity,
                "Enrolled learners exceeded capacity"
        );

        assertTrue(
                service.availableSeats() >= 0,
                "Available seats cannot be negative"
        );

        assertEquals(
                capacity,
                service.enrolledCount()
                        + service.availableSeats()
        );

        assertUniqueWaitlist(service);

        // Validate that the waitlist and enrolled statuses
        // do not overlap.
        Set<String> enrolledLearners = new HashSet<>();

        for (int i = 0; i < THREADS; i++) {

            String learnerId = "learner-" + i;

            if (service.statusOf(learnerId)
                    == EnrollmentStatus.ENROLLED) {

                enrolledLearners.add(learnerId);
            }
        }

        Set<String> waitlistedLearners =
                new HashSet<>(service.waitlistSnapshot());

        Set<String> overlap =
                new HashSet<>(enrolledLearners);

        overlap.retainAll(waitlistedLearners);

        assertTrue(
                overlap.isEmpty(),
                "A learner cannot be enrolled and waitlisted simultaneously"
        );
    }

    /**
     * Worker exceptions must propagate to the calling test.
     */
    @Test
    void workerExceptionsArePropagated()
            throws Exception {

        List<Callable<Void>> tasks = List.of(
                () -> {
                    throw new IllegalStateException("boom");
                }
        );

        assertThrows(
                Exception.class,
                () -> runConcurrently(tasks)
        );
    }

    private static long countOutcome(
            List<EnrollmentResult> results,
            EnrollmentResult.Outcome expected
    ) {

        return results.stream()
                .filter(result -> result.outcome() == expected)
                .count();
    }

    private static void assertUniqueWaitlist(
            CourseWaitlistService service
    ) {

        List<String> waitlist = service.waitlistSnapshot();

        Set<String> uniqueLearners =
                new HashSet<>(waitlist);

        assertEquals(
                waitlist.size(),
                uniqueLearners.size(),
                "Duplicate learners found in waitlist"
        );
    }
}
