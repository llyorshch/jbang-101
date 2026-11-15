package app.application.service;

import app.application.port.in.DeleteClient;
import app.application.port.out.CaseEngine;
import app.application.port.out.ClientRepository;
import app.domain.client.ClientNotDeletableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClientDeletionService implements DeleteClient {

  private final ClientRepository clients;
  private final CaseEngine cases;

  ClientDeletionService(ClientRepository clients, CaseEngine cases) {
    this.clients = clients;
    this.cases = cases;
  }

  /**
   * The client row and its case history go together, or not at all. The status check and the
   * compare-and-set delete keep a case from starting for a client that is being deleted.
   */
  @Override
  @Transactional
  public void delete(long id) {
    var client = clients.getById(id);
    client.ensureDeletable();
    if (cases.hasActiveCase(id)) {
      throw new ClientNotDeletableException(client.status());
    }
    cases.deleteEndedCasesOf(id);
    clients.remove(client);
  }
}
