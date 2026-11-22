
package com.example.coursewaitlist;

import java.util.List;

/**
 * Demonstrates course enrollment, duplicate prevention,
 * FIFO waitlisting, prerequisite eligibility and promotion.
 *
 * Run:
 * mvn clean compile
 *
 * Then:
 * java -cp target/classes com.example.coursewaitlist.Demo
 */
public final class Demo {

    private Demo() {
        // Utility class
    }

    public static void main(String[] args) {

        // Create a course offering with capacity of 2.
        CourseWaitlistService course =
                new CourseWaitlistService(2);

        System.out.println("===== COURSE WAITLIST DEMO =====");

        // 1. Enroll eligible learners.
        show("Enroll Alice", course.enroll("alice", true));
        show("Enroll Bob", course.enroll("bob", true));

        // 2. Duplicate enrollment prevention.
        show("Enroll Alice again", course.enroll("alice", true));

        // 3. Course is full: learners join FIFO waitlist.
        show("Enroll Carol", course.enroll("carol", true));
        show("Enroll Dave", course.enroll("dave", true));

        // 4. Learner without prerequisite cannot enroll.
        show("Enroll Erin (no prerequisite)",
                course.enroll("erin", false));

        // 5. Invalid learner ID.
        show("Enroll blank ID", course.enroll(" ", true));

        // 6. Carol's eligibility changes while waitlisted.
        course.updatePrerequisiteEligibility("carol", false);

        // 7. Drop Alice.
        // Carol is skipped because she is now ineligible.
        // Dave is promoted because he is the next eligible learner.
        show("Drop Alice", course.drop("alice"));

        // 8. Repeated drop must not free another seat.
        show("Drop Alice again", course.drop("alice"));

        // 9. Display final course state.
        printCourseState(course);

        // 10. Verify basic capacity invariant.
        verifyCapacity(course);

        System.out.println("Capacity invariant: OK");
        System.out.println("===== DEMO COMPLETED =====");
    }

    private static void show(
            String action,
            EnrollmentResult result
    ) {
        System.out.printf(
                "%-32s -> %-22s %s%n",
                action,
                result.outcome(),
                result.message()
        );

        result.promotedLearnerId().ifPresent(
                learnerId -> System.out.println(
                        "  Promoted learner: " + learnerId
                )
        );
    }

    private static void printCourseState(
            CourseWaitlistService course
    ) {
        System.out.println("\n===== FINAL COURSE STATE =====");

        System.out.println(
                "Capacity       : " + course.capacity()
        );

        System.out.println(
                "Enrolled count : " + course.enrolledCount()
        );

        System.out.println(
                "Available seats: " + course.availableSeats()
        );

        System.out.println(
                "Waitlist       : " + course.waitlistSnapshot()
        );

        System.out.println("\nLearner statuses:");

        List<String> learners = List.of(
                "alice",
                "bob",
                "carol",
                "dave",
                "erin"
        );

        for (String learner : learners) {
            System.out.printf(
                    "%-12s : %s%n",
                    learner,
                    course.statusOf(learner)
            );
        }
    }

    private static void verifyCapacity(
            CourseWaitlistService course
    ) {
        if (course.enrolledCount() > course.capacity()) {
            throw new IllegalStateException(
                    "Course capacity exceeded!"
            );
        }

        if (course.availableSeats() < 0) {
            throw new IllegalStateException(
                    "Available seats cannot be negative!"
            );
        }
    }
}
