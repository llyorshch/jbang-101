package app.adapter.out.flowable;

import app.domain.cases.CaseAction;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseStage;
import app.domain.cases.CaseState;
import app.domain.cases.CaseType;
import app.domain.cases.HistoryEntry;
import app.domain.cases.Outcome;
import app.domain.cases.SubStage;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.history.HistoricCaseInstance;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;

/** Reads Flowable runtime and history data into domain {@link CaseSnapshot}s. */
@Component
class FlowableSnapshots {

  private final CmmnRuntimeService runtime;
  private final CmmnTaskService tasks;
  private final CmmnHistoryService history;

  FlowableSnapshots(CmmnRuntimeService runtime, CmmnTaskService tasks, CmmnHistoryService history) {
    this.runtime = runtime;
    this.tasks = tasks;
    this.history = history;
  }

  CaseSnapshot toSnapshot(HistoricCaseInstance instance) {
    var type = PlanItems.caseType(instance.getCaseDefinitionKey());
    var running = instance.getEndTime() == null;
    var current = running ? current(type, instance.getId()) : Current.FINISHED;
    return new CaseSnapshot(
        instance.getId(),
        Long.parseLong(instance.getBusinessKey()),
        type,
        current.stage(),
        current.subStage().orElse(null),
        running ? CaseState.ACTIVE : endState(type, instance.getId()),
        current.actions(),
        instant(instance.getStartTime()),
        instant(instance.getEndTime()),
        history(type, instance.getId()));
  }

  private Current current(CaseType type, String caseId) {
    var activeStages =
        runtime
            .createPlanItemInstanceQuery()
            .caseInstanceId(caseId)
            .planItemDefinitionType(PlanItemDefinitionType.STAGE)
            .planItemInstanceStateActive()
            .list();
    if (activeStages.isEmpty()) {
      return Current.FINISHED;
    }
    var stage =
        activeStages.stream()
            .map(instance -> PlanItems.stage(instance.getPlanItemDefinitionId()))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(CaseStage.FINISHED);
    var activeSubStages =
        activeStages.stream()
            .sorted(Comparator.comparing(FlowableSnapshots::enteredAt).reversed())
            .map(instance -> PlanItems.subStage(instance.getPlanItemDefinitionId()))
            .flatMap(Optional::stream)
            .toList();
    var taskKeys =
        tasks.createTaskQuery().caseInstanceId(caseId).list().stream()
            .map(TaskInfo::getTaskDefinitionKey);
    var listenerIds =
        runtime
            .createPlanItemInstanceQuery()
            .caseInstanceId(caseId)
            .planItemDefinitionType(PlanItemDefinitionType.USER_EVENT_LISTENER)
            .planItemInstanceStateAvailable()
            .list()
            .stream()
            .map(PlanItemInstance::getPlanItemDefinitionId);
    var engineOffers =
        Stream.concat(taskKeys, listenerIds)
            .map(id -> PlanItems.action(type, id))
            .flatMap(Optional::stream)
            .toList();
    // Several sub-stages can run at once; the snapshot shows the most recently entered one.
    return new Current(
        stage,
        activeSubStages.stream().findFirst(),
        CaseAction.availableActions(engineOffers, activeSubStages));
  }

  private CaseState endState(CaseType type, String caseId) {
    if (type == CaseType.OFFBOARDING) {
      return CaseState.COMPLETED;
    }
    var succeeded =
        history
                .createHistoricPlanItemInstanceQuery()
                .planItemInstanceCaseInstanceId(caseId)
                .planItemInstanceDefinitionId(PlanItems.SUCCEEDED_MILESTONE)
                .planItemInstanceState(PlanItemInstanceState.COMPLETED)
                .count()
            > 0;
    return succeeded ? CaseState.SUCCEEDED : CaseState.FAILED;
  }

  /** Completed human tasks and user event occurrences, oldest first, with their notes. */
  private List<HistoryEntry> history(CaseType type, String caseId) {
    var steps =
        history
            .createHistoricPlanItemInstanceQuery()
            .planItemInstanceCaseInstanceId(caseId)
            .planItemInstanceDefinitionTypes(
                List.of(
                    PlanItemDefinitionType.HUMAN_TASK, PlanItemDefinitionType.USER_EVENT_LISTENER))
            .planItemInstanceState(PlanItemInstanceState.COMPLETED)
            .list();
    if (steps.isEmpty()) {
      return List.of();
    }
    Map<String, Map<String, Object>> taskVariables =
        history.createHistoricTaskInstanceQuery().caseInstanceId(caseId)
            .includeTaskLocalVariables().list().stream()
            .filter(task -> task.getSubScopeId() != null)
            .collect(
                Collectors.toMap(
                    HistoricTaskInstance::getSubScopeId,
                    HistoricTaskInstance::getTaskLocalVariables,
                    (first, second) -> first));
    Map<String, Object> listenerNotes =
        history.createHistoricVariableInstanceQuery().caseInstanceId(caseId)
            .variableName(CaseVariables.NOTE).list().stream()
            .filter(variable -> variable.getTaskId() == null && variable.getSubScopeId() != null)
            .filter(variable -> variable.getValue() != null)
            .collect(
                Collectors.toMap(
                    HistoricVariableInstance::getSubScopeId,
                    HistoricVariableInstance::getValue,
                    (first, second) -> first));
    return steps.stream()
        .sorted(
            Comparator.comparing(
                FlowableSnapshots::endedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(step -> entry(type, step, taskVariables, listenerNotes))
        .toList();
  }

  private static HistoryEntry entry(
      CaseType type,
      HistoricPlanItemInstance step,
      Map<String, Map<String, Object>> taskVariables,
      Map<String, Object> listenerNotes) {
    var variables = taskVariables.getOrDefault(step.getId(), Map.of());
    var note = variables.getOrDefault(CaseVariables.NOTE, listenerNotes.get(step.getId()));
    var outcome = variables.get(CaseVariables.OUTCOME);
    return new HistoryEntry(
        step.getName(),
        PlanItems.action(type, step.getPlanItemDefinitionId()).orElse(null),
        Objects.toString(note, null),
        outcome == null ? null : Outcome.valueOf(outcome.toString()),
        endedAt(step));
  }

  private static Instant endedAt(HistoricPlanItemInstance step) {
    return Stream.of(step.getCompletedTime(), step.getOccurredTime(), step.getEndedTime())
        .filter(Objects::nonNull)
        .findFirst()
        .map(Date::toInstant)
        .orElse(null);
  }

  /** When a stage instance became active; repeatable stages are created long before. */
  private static Date enteredAt(PlanItemInstance stage) {
    return Optional.ofNullable(stage.getLastStartedTime()).orElse(stage.getCreateTime());
  }

  private static Instant instant(Date date) {
    return Optional.ofNullable(date).map(Date::toInstant).orElse(null);
  }

  /** Current stage, sub-stage and allowed actions of a running case. */
  private record Current(CaseStage stage, Optional<SubStage> subStage, List<CaseAction> actions) {
    static final Current FINISHED = new Current(CaseStage.FINISHED, Optional.empty(), List.of());
  }
}
