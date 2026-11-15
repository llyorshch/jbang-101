package app.domain.client;

/** A client with an onboarding or offboarding in progress cannot be deleted. */
public class ClientNotDeletableException extends RuntimeException {

  public ClientNotDeletableException(ClientStatus current) {
    super("Client is " + current + ": finish its case before deleting it");
  }
}
