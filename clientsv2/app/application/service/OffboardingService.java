package app.application.service;

import app.application.port.in.StartOffboarding;
import app.application.port.out.CaseEngine;
import app.application.port.out.ClientRepository;
import app.domain.cases.ActiveCaseExistsException;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OffboardingService implements StartOffboarding {

  private final ClientRepository clients;
  private final CaseEngine cases;

  OffboardingService(ClientRepository clients, CaseEngine cases) {
    this.clients = clients;
    this.cases = cases;
  }

  /** The client transition and the new case commit together, or not at all. */
  @Override
  @Transactional
  public CaseSnapshot startOffboarding(long clientId) {
    var client = clients.getById(clientId);
    var moved = client.startOffboarding();
    if (cases.hasActiveCase(clientId)) {
      throw new ActiveCaseExistsException();
    }
    clients.replace(client, moved);
    var caseId = cases.start(CaseType.OFFBOARDING, clientId);
    return cases.find(CaseType.OFFBOARDING, caseId).orElseThrow();
  }
}
