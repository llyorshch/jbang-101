package app.application.port.in;

import app.domain.cases.CaseSnapshot;

/** Opens an onboarding case and moves the client to ONBOARDING, atomically. */
public interface StartOnboarding {

  CaseSnapshot startOnboarding(long clientId);
}
