package com.example.coursewaitlist;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe in-memory service for one course offering.
 *
 * All state-changing operations are synchronized on this service instance.
 * This protects capacity and FIFO promotion within a single JVM.
 */
public final class CourseWaitlistService {

    private final int capacity;

    // One registration record per learner for the lifetime of this offering.
    private final Map<String, EnrollmentStatus> statuses = new HashMap<>();

    // Current prerequisite eligibility for learners on the waitlist.
    private final Map<String, Boolean> currentPrerequisiteEligibility =
            new HashMap<>();

    // FIFO queue: earliest eligible learner is considered first.
    private final Deque<String> waitlist = new ArrayDeque<>();

    public CourseWaitlistService(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be greater than zero"
            );
        }

        this.capacity = capacity;
    }

    /**
     * Enrolls an eligible learner if a seat is available.
     * Otherwise adds the learner to the FIFO waitlist.
     */
    public synchronized EnrollmentResult enroll(
            String learnerId,
            boolean prerequisiteSatisfied
    ) {
        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.INVALID_REQUEST,
                    null,
                    "Learner ID must not be blank"
            );
        }

        // Enforce one registration attempt that creates a record per learner.
        // This includes learners who previously dropped or were skipped.
        EnrollmentStatus existingStatus = statuses.get(learnerId);

        if (existingStatus != null) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.ALREADY_REGISTERED,
                    existingStatus,
                    "Learner already has a registration record"
            );
        }

        // A failed prerequisite check does not create a registration record.
        // The learner may retry after satisfying the prerequisite.
        if (!prerequisiteSatisfied) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.PREREQUISITE_NOT_MET,
                    null,
                    "Required prerequisite has not been completed"
            );
        }

        if (enrolledCount() < capacity) {
            statuses.put(learnerId, EnrollmentStatus.ENROLLED);

            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.ENROLLED,
                    EnrollmentStatus.ENROLLED,
                    "Enrollment successful"
            );
        }

        statuses.put(learnerId, EnrollmentStatus.WAITLISTED);
        currentPrerequisiteEligibility.put(learnerId, true);
        waitlist.addLast(learnerId);

        return EnrollmentResult.of(
                EnrollmentResult.Outcome.WAITLISTED,
                EnrollmentStatus.WAITLISTED,
                "Course is full; learner added to the waitlist"
        );
    }

    /**
     * Drops an enrolled learner and promotes the earliest eligible
     * learner from the waitlist, if one exists.
     */
    public synchronized EnrollmentResult drop(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.INVALID_REQUEST,
                    null,
                    "Learner ID must not be blank"
            );
        }

        EnrollmentStatus currentStatus = statuses.get(learnerId);

        if (currentStatus == EnrollmentStatus.DROPPED) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.ALREADY_DROPPED,
                    EnrollmentStatus.DROPPED,
                    "Learner has already dropped this course"
            );
        }

        if (currentStatus != EnrollmentStatus.ENROLLED) {
            return EnrollmentResult.of(
                    EnrollmentResult.Outcome.NOT_ENROLLED,
                    currentStatus,
                    "Learner is not currently enrolled; no state was changed"
            );
        }

        // Free the seat first.
        statuses.put(learnerId, EnrollmentStatus.DROPPED);

        // Fill the newly available seat, if an eligible learner exists.
        String promotedLearnerId = promoteNextEligible();

        EnrollmentResult result = EnrollmentResult.of(
                EnrollmentResult.Outcome.DROPPED,
                EnrollmentStatus.DROPPED,
                "Learner dropped successfully"
        );

        return result.withPromotion(promotedLearnerId);
    }

    /**
     * Examines waitlisted learners in FIFO order.
     *
     * Ineligible learners are marked SKIPPED and removed from the queue.
     * Processing continues until an eligible learner is promoted
     * or the queue becomes empty.
     */
    private String promoteNextEligible() {
        while (enrolledCount() < capacity && !waitlist.isEmpty()) {

            String nextLearnerId = waitlist.removeFirst();

            // Defensive check against stale queue entries.
            if (statuses.get(nextLearnerId) != EnrollmentStatus.WAITLISTED) {
                continue;
            }

            boolean eligible = currentPrerequisiteEligibility
                    .getOrDefault(nextLearnerId, false);

            if (!eligible) {
                statuses.put(
                        nextLearnerId,
                        EnrollmentStatus.SKIPPED
                );

                currentPrerequisiteEligibility.remove(nextLearnerId);
                continue;
            }

            statuses.put(
                    nextLearnerId,
                    EnrollmentStatus.ENROLLED
            );

            currentPrerequisiteEligibility.remove(nextLearnerId);

            return nextLearnerId;
        }

        return null;
    }

    /**
     * Updates eligibility for a learner currently on the waitlist.
     * Eligibility is checked again at promotion time.
     */
    public synchronized void updatePrerequisiteEligibility(
            String learnerId,
            boolean eligible
    ) {
        if (learnerId == null || learnerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Learner ID must not be blank"
            );
        }

        if (statuses.get(learnerId) != EnrollmentStatus.WAITLISTED) {
            throw new IllegalArgumentException(
                    "Learner is not currently waitlisted: " + learnerId
            );
        }

        currentPrerequisiteEligibility.put(learnerId, eligible);
    }

    public synchronized int enrolledCount() {
        int count = 0;

        for (EnrollmentStatus status : statuses.values()) {
            if (status == EnrollmentStatus.ENROLLED) {
                count++;
            }
        }

        return count;
    }

    public synchronized int availableSeats() {
        return capacity - enrolledCount();
    }

    public synchronized EnrollmentStatus statusOf(String learnerId) {
        return statuses.get(learnerId);
    }

    public synchronized List<String> waitlistSnapshot() {
        return List.copyOf(waitlist);
    }

    public int capacity() {
        return capacity;
    }
}