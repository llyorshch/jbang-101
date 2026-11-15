package app.domain.cases;

import static app.domain.cases.CaseType.OFFBOARDING;
import static app.domain.cases.CaseType.ONBOARDING;
import static app.domain.cases.SubStage.AMENDMENT_REQUIRED;
import static app.domain.cases.SubStage.DOCUMENTS_REQUIRED;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whitelist of operator actions and their rules: which case type they belong to, which active
 * sub-stages block them, and which need an outcome.
 */
public enum CaseAction {
  SUBMIT(ONBOARDING, "submit"),
  REQUEST_DOCUMENTS(ONBOARDING, "request-documents", DOCUMENTS_REQUIRED),
  CONFIRM_DOCUMENTS(ONBOARDING, "confirm-documents"),
  REQUEST_AMENDMENT(ONBOARDING, "request-amendment", AMENDMENT_REQUIRED),
  CONFIRM_AMENDMENT(ONBOARDING, "confirm-amendment"),
  DECIDE(ONBOARDING, "decide", DOCUMENTS_REQUIRED, AMENDMENT_REQUIRED),
  START_CHECKS(OFFBOARDING, "start-checks"),
  COMPLETE_CHECKS(OFFBOARDING, "complete-checks");

  private final CaseType caseType;
  private final String actionName;
  private final Set<SubStage> blockedBy;

  CaseAction(CaseType caseType, String actionName, SubStage... blockedBy) {
    this.caseType = caseType;
    this.actionName = actionName;
    this.blockedBy = Set.of(blockedBy);
  }

  public CaseType caseType() {
    return caseType;
  }

  /** Name used in URLs and in {@code availableActions}. */
  public String actionName() {
    return actionName;
  }

  /**
   * Resolves an action name sent by an operator.
   *
   * @throws InvalidActionException when the name is not an action of this case type
   */
  public static CaseAction parse(CaseType type, String actionName) {
    return Arrays.stream(values())
        .filter(action -> action.caseType == type && action.actionName.equals(actionName))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidActionException(
                    "Unknown action",
                    Map.of("action", "is not a known " + type.name().toLowerCase() + " action")));
  }

  /**
   * The actions allowed now: those the case engine offers, minus those blocked by an active
   * sub-stage. Returned in declaration order.
   */
  public static List<CaseAction> availableActions(
      Collection<CaseAction> engineOffers, Collection<SubStage> activeSubStages) {
    return engineOffers.stream()
        .distinct()
        .filter(action -> activeSubStages.stream().noneMatch(action.blockedBy::contains))
        .sorted()
        .toList();
  }

  /**
   * {@code decide} requires an outcome; every other action must not carry one.
   *
   * @throws InvalidActionException when the rule is broken
   */
  public void checkOutcome(Outcome outcome) {
    if (this == DECIDE && outcome == null) {
      throw new InvalidActionException(
          "Invalid request content", Map.of("outcome", "is required for decide"));
    }
    if (this != DECIDE && outcome != null) {
      throw new InvalidActionException(
          "Invalid request content", Map.of("outcome", "is only allowed for decide"));
    }
  }
}
