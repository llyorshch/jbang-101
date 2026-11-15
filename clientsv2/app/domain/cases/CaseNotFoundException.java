package app.domain.cases;

/** The requested case does not exist (for the requested case type). */
public class CaseNotFoundException extends RuntimeException {

  public CaseNotFoundException(String caseId) {
    super("Case " + caseId + " not found");
  }
}
