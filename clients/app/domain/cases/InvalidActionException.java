package app.domain.cases;

import java.util.Map;

/** An action request that is invalid regardless of the case state (unknown name, bad outcome). */
public class InvalidActionException extends RuntimeException {

  private final Map<String, String> errors;

  public InvalidActionException(String message, Map<String, String> errors) {
    super(message);
    this.errors = Map.copyOf(errors);
  }

  /** Field name to error message, for the {@code errors} member of the problem response. */
  public Map<String, String> errors() {
    return errors;
  }
}
