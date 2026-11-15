package app.application.service;

import app.application.port.in.CaseActionCommand;
import app.application.port.in.PerformCaseAction;
import app.application.port.out.CaseEngine;
import app.domain.cases.ActionNotAvailableException;
import app.domain.cases.CaseAction;
import app.domain.cases.CaseNotFoundException;
import app.domain.cases.CaseSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CaseActionService implements PerformCaseAction {

  private final CaseEngine cases;

  CaseActionService(CaseEngine cases) {
    this.cases = cases;
  }

  /**
   * Checks in order: known action (400), outcome rule (400), existing case (404), action available
   * now (409). Client status changes triggered by the engine commit in the same transaction.
   */
  @Override
  @Transactional
  public CaseSnapshot perform(CaseActionCommand command) {
    var action = CaseAction.parse(command.type(), command.actionName());
    action.checkOutcome(command.outcome());
    var current =
        cases
            .find(command.type(), command.caseId())
            .orElseThrow(() -> new CaseNotFoundException(command.caseId()));
    if (!current.allows(action)) {
      throw new ActionNotAvailableException(action.actionName());
    }
    cases.perform(command.caseId(), action, command.note(), command.outcome());
    return cases.find(command.type(), command.caseId()).orElseThrow();
  }
}
