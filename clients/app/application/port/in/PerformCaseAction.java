package app.application.port.in;

import app.domain.cases.CaseSnapshot;

/** Performs an operator action and returns the updated case. */
public interface PerformCaseAction {

  CaseSnapshot perform(CaseActionCommand command);
}
