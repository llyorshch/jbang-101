package app.adapter.out.flowable;

import app.domain.cases.CaseAction;
import app.domain.cases.CaseStage;
import app.domain.cases.CaseType;
import app.domain.cases.SubStage;
import java.util.Arrays;
import java.util.Optional;

/** Maps domain concepts to the ids used in the CMMN models. Keeps CMMN ids inside this adapter. */
final class PlanItems {

  static final String SUCCEEDED_MILESTONE = "onboardingSucceeded";

  /** How an action is executed in the engine. */
  enum Kind {
    HUMAN_TASK,
    USER_EVENT
  }

  /** The plan item an action drives. */
  record Target(String planItemDefinitionId, Kind kind) {}

  private PlanItems() {}

  static String definitionKey(CaseType type) {
    return switch (type) {
      case ONBOARDING -> "onboarding";
      case OFFBOARDING -> "offboarding";
    };
  }

  static CaseType caseType(String definitionKey) {
    return Arrays.stream(CaseType.values())
        .filter(type -> definitionKey(type).equals(definitionKey))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unexpected case definition"));
  }

  static Target target(CaseAction action) {
    return switch (action) {
      case SUBMIT -> new Target("submitDraft", Kind.HUMAN_TASK);
      case REQUEST_DOCUMENTS -> new Target("requestDocuments", Kind.USER_EVENT);
      case CONFIRM_DOCUMENTS -> new Target("confirmDocumentsReceived", Kind.HUMAN_TASK);
      case REQUEST_AMENDMENT -> new Target("requestAmendment", Kind.USER_EVENT);
      case CONFIRM_AMENDMENT -> new Target("confirmAmendmentReceived", Kind.HUMAN_TASK);
      case DECIDE -> new Target("decide", Kind.HUMAN_TASK);
      case START_CHECKS -> new Target("startChecks", Kind.HUMAN_TASK);
      case COMPLETE_CHECKS -> new Target("completeChecks", Kind.HUMAN_TASK);
    };
  }

  static Optional<CaseAction> action(CaseType type, String planItemDefinitionId) {
    return Arrays.stream(CaseAction.values())
        .filter(action -> action.caseType() == type)
        .filter(action -> target(action).planItemDefinitionId().equals(planItemDefinitionId))
        .findFirst();
  }

  static Optional<CaseStage> stage(String stageId) {
    return Optional.ofNullable(
        switch (stageId) {
          case "draft" -> CaseStage.DRAFT;
          case "open" -> CaseStage.OPEN;
          case "opened" -> CaseStage.OPENED;
          case "checksOngoing" -> CaseStage.CHECKS_ONGOING;
          case "finished" -> CaseStage.FINISHED;
          default -> null;
        });
  }

  static Optional<SubStage> subStage(String stageId) {
    return Optional.ofNullable(
        switch (stageId) {
          case "documentsRequired" -> SubStage.DOCUMENTS_REQUIRED;
          case "amendmentRequired" -> SubStage.AMENDMENT_REQUIRED;
          default -> null;
        });
  }
}
