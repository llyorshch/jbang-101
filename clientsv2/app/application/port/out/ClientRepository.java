package app.application.port.out;

import app.domain.client.Client;
import app.domain.client.ClientNotFoundException;
import app.domain.client.ConcurrentClientUpdateException;
import java.util.List;
import java.util.Optional;

/** Persistence of clients. */
public interface ClientRepository {

  Optional<Client> findById(long id);

  /**
   * @throws ClientNotFoundException when the client does not exist
   */
  default Client getById(long id) {
    return findById(id).orElseThrow(() -> new ClientNotFoundException(id));
  }

  List<Client> findAllNewestFirst();

  /** Stores a new client and returns it with its id. */
  Client add(Client client);

  /**
   * Stores {@code after} only if the stored client still equals {@code before} (compare-and-set
   * on the status), so two concurrent transitions cannot both succeed.
   *
   * @throws ConcurrentClientUpdateException when the stored client no longer matches {@code before}
   */
  void replace(Client before, Client after);
}
