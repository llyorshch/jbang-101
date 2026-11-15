package app.application.service;

import app.application.port.in.FindClients;
import app.application.port.in.RegisterClient;
import app.application.port.in.RegisterClientCommand;
import app.application.port.out.ClientRepository;
import app.domain.client.Client;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClientService implements RegisterClient, FindClients {

  private final ClientRepository clients;

  ClientService(ClientRepository clients) {
    this.clients = clients;
  }

  @Override
  @Transactional
  public Client register(RegisterClientCommand command) {
    var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    return clients.add(Client.register(command.name(), command.email(), command.country(), now));
  }

  @Override
  @Transactional(readOnly = true)
  public List<Client> all() {
    return clients.findAllNewestFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Client byId(long id) {
    return clients.getById(id);
  }
}
