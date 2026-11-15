package app.domain.client;

/** The requested client does not exist. */
public class ClientNotFoundException extends RuntimeException {

  public ClientNotFoundException(long id) {
    super("Client " + id + " not found");
  }
}
