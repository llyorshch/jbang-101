package app.domain.client;

import java.util.Set;

/** The client's current status does not allow the requested transition. */
public class InvalidClientTransitionException extends RuntimeException {

  public InvalidClientTransitionException(ClientStatus current, Set<ClientStatus> allowedFrom) {
    super("Client is " + current + ", expected one of " + allowedFrom.stream().sorted().toList());
  }
}
