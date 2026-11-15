package app.application.port.in;

import app.domain.cases.CaseSnapshot;

/** Opens an offboarding case and moves the client to OFFBOARDING, atomically. */
public interface StartOffboarding {

  CaseSnapshot startOffboarding(long clientId);
}
