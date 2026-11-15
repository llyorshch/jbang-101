package app.domain.cases;

import java.time.Instant;

/** A completed step of a case. {@code action} is null for steps no operator action maps to. */
public record HistoryEntry(
    String step, CaseAction action, String note, Outcome outcome, Instant completedAt) {}
