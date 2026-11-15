package app.domain.cases;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Point-in-time view of a case. {@code subStage} (the most recently entered active sub-stage) and
 * {@code endedAt} are null when not applicable.
 */
public record CaseSnapshot(
    String id,
    long clientId,
    CaseType type,
    CaseStage stage,
    SubStage subStage,
    CaseState state,
    List<CaseAction> availableActions,
    Instant startedAt,
    Instant endedAt,
    List<HistoryEntry> history) {

  public CaseSnapshot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(state, "state");
    availableActions = List.copyOf(availableActions);
    history = List.copyOf(history);
  }

  public boolean allows(CaseAction action) {
    return availableActions.contains(action);
  }
}
