package app.application.port.in;

import app.domain.cases.Outcome;

/** Called by the case engine when a case reaches its finished stage. */
public interface CompleteCase {

  void onboardingFinished(long clientId, Outcome outcome);

  void offboardingFinished(long clientId);
}
