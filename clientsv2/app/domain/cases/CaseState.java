package app.domain.cases;

/** Overall state of a case: running, or how it ended. */
public enum CaseState {
  ACTIVE,
  SUCCEEDED,
  FAILED,
  COMPLETED
}
