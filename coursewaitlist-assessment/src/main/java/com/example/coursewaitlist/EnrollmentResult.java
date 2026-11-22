package com.example.coursewaitlist;

import java.util.Optional;

public record EnrollmentResult(
        Outcome outcome,
        EnrollmentStatus status,
        String message,
        Optional<String> promotedLearnerId
) {
    public enum Outcome {
        ENROLLED,
        WAITLISTED,
        ALREADY_REGISTERED,
        PREREQUISITE_NOT_MET,
        DROPPED,
        ALREADY_DROPPED,
        NOT_ENROLLED,
        INVALID_REQUEST
    }

    public EnrollmentResult {
        promotedLearnerId = promotedLearnerId == null
                ? Optional.empty()
                : promotedLearnerId;
    }

    public static EnrollmentResult of(
            Outcome outcome,
            EnrollmentStatus status,
            String message
    ) {
        return new EnrollmentResult(
                outcome,
                status,
                message,
                Optional.empty()
        );
    }

    public EnrollmentResult withPromotion(String learnerId) {
        return new EnrollmentResult(
                outcome,
                status,
                message,
                Optional.ofNullable(learnerId)
        );
    }
}