package app.application.service;

import app.application.port.in.CompleteCase;
import app.application.port.out.ClientRepository;
import app.domain.cases.Outcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the client transition when a case ends; joins the engine's transaction. */
@Service
class CaseCompletionService implements CompleteCase {

  private final ClientRepository clients;

  CaseCompletionService(ClientRepository clients) {
    this.clients = clients;
  }

  @Override
  @Transactional
  public void onboardingFinished(long clientId, Outcome outcome) {
    var client = clients.getById(clientId);
    clients.replace(client, client.finishOnboarding(outcome));
  }

  @Override
  @Transactional
  public void offboardingFinished(long clientId) {
    var client = clients.getById(clientId);
    clients.replace(client, client.finishOffboarding());
  }
}
