package com.example.coursewaitlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class CourseWaitlistServiceTest {

    @Test
    void enrollsWhenCapacityIsAvailable() {
        var service = new CourseWaitlistService(2);

        var result = service.enroll("A", true);

        assertEquals(EnrollmentResult.Outcome.ENROLLED, result.outcome());
        assertEquals(EnrollmentStatus.ENROLLED, service.statusOf("A"));
        assertEquals(1, service.enrolledCount());
        assertEquals(1, service.availableSeats());
    }

    @Test
    void eligibleLearnerJoinsWaitlistWhenCourseIsFull() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        var result = service.enroll("B", true);

        assertEquals(EnrollmentResult.Outcome.WAITLISTED, result.outcome());
        assertEquals(List.of("B"), service.waitlistSnapshot());
        assertEquals(1, service.enrolledCount());
    }

    @Test
    void duplicateEnrollmentDoesNotCreateAnotherRecord() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);

        var duplicate = service.enroll("B", true);

        assertEquals(
                EnrollmentResult.Outcome.ALREADY_REGISTERED,
                duplicate.outcome()
        );
        assertEquals(List.of("B"), service.waitlistSnapshot());
    }

    @Test
    void droppingLearnerPromotesEarliestWaitlistedLearner() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);
        service.enroll("C", true);

        var result = service.drop("A");

        assertEquals(EnrollmentResult.Outcome.DROPPED, result.outcome());
        assertEquals("B", result.promotedLearnerId().orElseThrow());

        assertEquals(EnrollmentStatus.DROPPED, service.statusOf("A"));
        assertEquals(EnrollmentStatus.ENROLLED, service.statusOf("B"));
        assertEquals(List.of("C"), service.waitlistSnapshot());

        assertEquals(1, service.enrolledCount());
        assertEquals(0, service.availableSeats());
    }

    @Test
    void prerequisiteFailureDoesNotCreateRegistrationRecord() {
        var service = new CourseWaitlistService(1);

        var result = service.enroll("A", false);

        assertEquals(
                EnrollmentResult.Outcome.PREREQUISITE_NOT_MET,
                result.outcome()
        );
        assertNull(service.statusOf("A"));
        assertEquals(1, service.availableSeats());
    }

    @Test
    void learnerCanRetryAfterInitiallyFailingPrerequisite() {
        var service = new CourseWaitlistService(1);

        var firstAttempt = service.enroll("A", false);
        var secondAttempt = service.enroll("A", true);

        assertEquals(
                EnrollmentResult.Outcome.PREREQUISITE_NOT_MET,
                firstAttempt.outcome()
        );
        assertEquals(EnrollmentResult.Outcome.ENROLLED, secondAttempt.outcome());
    }

    @Test
    void ineligibleLearnerAtQueueHeadIsSkipped() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);
        service.enroll("C", true);

        service.updatePrerequisiteEligibility("B", false);

        var result = service.drop("A");

        assertEquals(EnrollmentStatus.SKIPPED, service.statusOf("B"));
        assertEquals(EnrollmentStatus.ENROLLED, service.statusOf("C"));
        assertEquals("C", result.promotedLearnerId().orElseThrow());
        assertTrue(service.waitlistSnapshot().isEmpty());
    }

    @Test
    void multipleIneligibleLearnersAreSkippedUntilEligibleLearnerIsFound() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);
        service.enroll("C", true);
        service.enroll("D", true);

        service.updatePrerequisiteEligibility("B", false);
        service.updatePrerequisiteEligibility("C", false);

        var result = service.drop("A");

        assertEquals(EnrollmentStatus.SKIPPED, service.statusOf("B"));
        assertEquals(EnrollmentStatus.SKIPPED, service.statusOf("C"));
        assertEquals(EnrollmentStatus.ENROLLED, service.statusOf("D"));
        assertEquals("D", result.promotedLearnerId().orElseThrow());
    }

    @Test
    void waitlistedLearnerCanBecomeEligibleAgainBeforePromotion() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);

        service.updatePrerequisiteEligibility("B", false);
        service.updatePrerequisiteEligibility("B", true);

        var result = service.drop("A");

        assertEquals("B", result.promotedLearnerId().orElseThrow());
        assertEquals(EnrollmentStatus.ENROLLED, service.statusOf("B"));
    }

    @Test
    void droppedLearnerCannotRegisterAgain() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.drop("A");

        var result = service.enroll("A", true);

        assertEquals(
                EnrollmentResult.Outcome.ALREADY_REGISTERED,
                result.outcome()
        );
        assertEquals(EnrollmentStatus.DROPPED, service.statusOf("A"));
    }

    @Test
    void repeatedDropReturnsAlreadyDropped() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);

        assertEquals(
                EnrollmentResult.Outcome.DROPPED,
                service.drop("A").outcome()
        );

        int seatsAfterFirstDrop = service.availableSeats();

        assertEquals(
                EnrollmentResult.Outcome.ALREADY_DROPPED,
                service.drop("A").outcome()
        );

        assertEquals(seatsAfterFirstDrop, service.availableSeats());
    }

    @Test
    void droppingWaitlistedLearnerDoesNotChangeQueue() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);

        var result = service.drop("B");

        assertEquals(EnrollmentResult.Outcome.NOT_ENROLLED, result.outcome());
        assertEquals(List.of("B"), service.waitlistSnapshot());
        assertEquals(1, service.enrolledCount());
    }

    @Test
    void invalidLearnerIdsAreRejected() {
        var service = new CourseWaitlistService(1);

        assertEquals(
                EnrollmentResult.Outcome.INVALID_REQUEST,
                service.enroll(null, true).outcome()
        );

        assertEquals(
                EnrollmentResult.Outcome.INVALID_REQUEST,
                service.enroll(" ", true).outcome()
        );

        assertEquals(
                EnrollmentResult.Outcome.INVALID_REQUEST,
                service.drop(null).outcome()
        );

        assertEquals(
                EnrollmentResult.Outcome.INVALID_REQUEST,
                service.drop(" ").outcome()
        );
    }

    @Test
    void constructorRejectsInvalidCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseWaitlistService(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new CourseWaitlistService(-1)
        );
    }

    @Test
    void eligibilityUpdateRejectsUnknownOrNonWaitlistedLearners() {
        var service = new CourseWaitlistService(1);

        service.enroll("A", true);
        service.enroll("B", true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrerequisiteEligibility(" ", false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrerequisiteEligibility("A", false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePrerequisiteEligibility("UNKNOWN", false)
        );
    }
    @Test
    void concurrentRequestsForSameLearnerCreateOnlyOneRegistration()
            throws Exception {

        var service = new CourseWaitlistService(1);
        int requestCount = 50;

        ExecutorService pool = Executors.newFixedThreadPool(10);

        try {
            List<Callable<EnrollmentResult>> tasks = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                tasks.add(() -> service.enroll("SAME_LEARNER", true));
            }

            List<Future<EnrollmentResult>> futures = pool.invokeAll(tasks);

            long successfulEnrollments = 0;

            for (Future<EnrollmentResult> future : futures) {
                EnrollmentResult result = future.get();

                if (result.outcome() == EnrollmentResult.Outcome.ENROLLED) {
                    successfulEnrollments++;
                }
            }

            assertEquals(1, successfulEnrollments);
            assertEquals(1, service.enrolledCount());
            assertEquals(
                    EnrollmentStatus.ENROLLED,
                    service.statusOf("SAME_LEARNER")
            );

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDifferentLearnersNeverExceedCapacity() throws Exception {
        int capacity = 10;
        int requestCount = 100;

        var service = new CourseWaitlistService(capacity);
        ExecutorService pool = Executors.newFixedThreadPool(12);

        try {
            List<Callable<EnrollmentResult>> tasks = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                final int id = i;
                tasks.add(() -> service.enroll("L" + id, true));
            }

            List<Future<EnrollmentResult>> futures = pool.invokeAll(tasks);

            for (Future<EnrollmentResult> future : futures) {
                future.get();
            }

            assertEquals(capacity, service.enrolledCount());
            assertEquals(requestCount - capacity, service.waitlistSnapshot().size());
            assertTrue(service.enrolledCount() <= service.capacity());

        } finally {
            pool.shutdownNow();
        }
    }


}