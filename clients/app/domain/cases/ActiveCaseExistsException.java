package app.domain.cases;

/** A client can have at most one active case. */
public class ActiveCaseExistsException extends RuntimeException {

  public ActiveCaseExistsException() {
    super("Client already has an active case");
  }
}
