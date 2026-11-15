package app.application.port.in;

import app.domain.client.ClientNotDeletableException;
import app.domain.client.ClientNotFoundException;

/** Permanently deletes a client and the history of its finished cases. */
public interface DeleteClient {

  /**
   * @throws ClientNotFoundException when the client does not exist
   * @throws ClientNotDeletableException when the client has a case in progress
   */
  void delete(long id);
}
