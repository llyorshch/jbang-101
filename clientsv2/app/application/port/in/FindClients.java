package app.application.port.in;

import app.domain.client.Client;
import app.domain.client.ClientNotFoundException;
import java.util.List;

/** Reads clients. */
public interface FindClients {

  /** All clients, newest first. */
  List<Client> all();

  /**
   * @throws ClientNotFoundException when the client does not exist
   */
  Client byId(long id);
}
