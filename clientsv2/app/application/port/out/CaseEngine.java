package app.application.port.out;

import app.domain.cases.ActionNotAvailableException;
import app.domain.cases.CaseAction;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseType;
import app.domain.cases.Outcome;
import java.util.List;
import java.util.Optional;

/** The case-management engine that runs onboarding and offboarding cases. */
public interface CaseEngine {

  boolean hasActiveCase(long clientId);

  /** Starts a case for the client and returns its id. */
  String start(CaseType type, long clientId);

  /** Finds a running or ended case; empty when it does not exist or is of another type. */
  Optional<CaseSnapshot> find(CaseType type, String caseId);

  /** Cases newest first; an empty {@code active} means running and ended cases. */
  List<CaseSnapshot> list(CaseType type, Optional<Boolean> active);

  /** All cases of a client, of every type, newest first. */
  List<CaseSnapshot> listForClient(long clientId);

  /**
   * Executes an action the caller has checked is available, storing the operator note (and the
   * outcome) with the step.
   *
   * @throws ActionNotAvailableException when the engine no longer offers the action
   */
  void perform(String caseId, CaseAction action, String note, Outcome outcome);
}
