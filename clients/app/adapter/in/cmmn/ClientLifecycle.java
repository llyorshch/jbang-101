package app.adapter.in.cmmn;

import app.application.port.in.CompleteCase;
import app.domain.cases.Outcome;
import org.flowable.common.engine.api.variable.VariableContainer;
import org.springframework.stereotype.Component;

/**
 * Driving adapter: CMMN service tasks call {@code ${clientLifecycle.onboardingFinished(caseInstance)}}
 * and {@code ${clientLifecycle.offboardingFinished(caseInstance)}} when a case reaches its finished
 * stage. It runs inside the transaction of the operator action that ended the case.
 */
@Component("clientLifecycle")
public class ClientLifecycle {

  /** Case variable names written by the case engine adapter (duplicated: adapters stay apart). */
  private static final String CLIENT_ID = "clientId";

  private static final String OUTCOME = "outcome";

  private final CompleteCase completeCase;

  ClientLifecycle(CompleteCase completeCase) {
    this.completeCase = completeCase;
  }

  public void onboardingFinished(VariableContainer caseInstance) {
    var outcome = Outcome.valueOf((String) caseInstance.getVariable(OUTCOME));
    completeCase.onboardingFinished(clientId(caseInstance), outcome);
  }

  public void offboardingFinished(VariableContainer caseInstance) {
    completeCase.offboardingFinished(clientId(caseInstance));
  }

  private static long clientId(VariableContainer caseInstance) {
    return ((Number) caseInstance.getVariable(CLIENT_ID)).longValue();
  }
}
