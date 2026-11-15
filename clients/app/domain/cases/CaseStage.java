package app.domain.cases;

/** Top-level stages: DRAFT, OPEN, FINISHED (onboarding); OPENED, CHECKS_ONGOING, FINISHED (offboarding). */
public enum CaseStage {
  DRAFT,
  OPEN,
  OPENED,
  CHECKS_ONGOING,
  FINISHED
}
