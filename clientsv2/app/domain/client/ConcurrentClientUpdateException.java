package app.domain.client;

/** The client changed between reading it and storing a transition (lost the compare-and-set). */
public class ConcurrentClientUpdateException extends RuntimeException {

  public ConcurrentClientUpdateException(long id) {
    super("Client " + id + " was changed concurrently, retry the request");
  }
}
