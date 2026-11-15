package app.domain.cases;

/** The action exists but cannot be performed in the case's current state. */
public class ActionNotAvailableException extends RuntimeException {

  public ActionNotAvailableException(String actionName) {
    super("Action '" + actionName + "' is not available");
  }
}
