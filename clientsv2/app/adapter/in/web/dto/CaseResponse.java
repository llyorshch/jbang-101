package app.adapter.in.web.dto;

import app.domain.cases.CaseAction;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseStage;
import app.domain.cases.CaseState;
import app.domain.cases.CaseType;
import app.domain.cases.Outcome;
import app.domain.cases.SubStage;
import java.time.Instant;
import java.util.List;

/** Read model of an onboarding or offboarding case, as exposed by the REST API. */
public record CaseResponse(
    String id,
    long clientId,
    CaseType type,
    CaseStage stage,
    SubStage subStage,
    CaseState state,
    List<String> availableActions,
    Instant startedAt,
    Instant endedAt,
    List<HistoryEntry> history) {

  /** A completed step of the case. */
  public record HistoryEntry(
      String step, String action, String note, Outcome outcome, Instant completedAt) {}

  public static CaseResponse from(CaseSnapshot c) {
    return new CaseResponse(
        c.id(),
        c.clientId(),
        c.type(),
        c.stage(),
        c.subStage(),
        c.state(),
        c.availableActions().stream().map(CaseAction::actionName).toList(),
        c.startedAt(),
        c.endedAt(),
        c.history().stream()
            .map(
                h ->
                    new HistoryEntry(
                        h.step(),
                        h.action() == null ? null : h.action().actionName(),
                        h.note(),
                        h.outcome(),
                        h.completedAt()))
            .toList());
  }
}
