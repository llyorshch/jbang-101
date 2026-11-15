package app.adapter.out.flowable;

import app.application.port.out.CaseEngine;
import app.domain.cases.ActionNotAvailableException;
import app.domain.cases.CaseAction;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseType;
import app.domain.cases.Outcome;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.history.HistoricCaseInstanceQuery;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Flowable CMMN implementation of the case engine port. Cases use businessKey = client id and the
 * case variable {@code clientId}; the application never sets any other variable directly.
 */
@Component
class FlowableCaseEngine implements CaseEngine {

  private static final Logger log = LoggerFactory.getLogger(FlowableCaseEngine.class);

  private final CmmnRuntimeService runtime;
  private final CmmnTaskService tasks;
  private final CmmnHistoryService history;
  private final FlowableSnapshots snapshots;

  FlowableCaseEngine(
      CmmnRuntimeService runtime,
      CmmnTaskService tasks,
      CmmnHistoryService history,
      FlowableSnapshots snapshots) {
    this.runtime = runtime;
    this.tasks = tasks;
    this.history = history;
    this.snapshots = snapshots;
  }

  @Override
  public boolean hasActiveCase(long clientId) {
    return runtime.createCaseInstanceQuery().caseInstanceBusinessKey(String.valueOf(clientId)).count()
        > 0;
  }

  @Override
  public String start(CaseType type, long clientId) {
    var instance =
        runtime
            .createCaseInstanceBuilder()
            .caseDefinitionKey(PlanItems.definitionKey(type))
            .businessKey(String.valueOf(clientId))
            .variable(CaseVariables.CLIENT_ID, clientId)
            .start();
    log.info("Started {} case {} for client {}", type, instance.getId(), clientId);
    return instance.getId();
  }

  @Override
  public Optional<CaseSnapshot> find(CaseType type, String caseId) {
    return Optional.ofNullable(
            history
                .createHistoricCaseInstanceQuery()
                .caseInstanceId(caseId)
                .caseDefinitionKey(PlanItems.definitionKey(type))
                .singleResult())
        .map(snapshots::toSnapshot);
  }

  @Override
  public List<CaseSnapshot> list(CaseType type, Optional<Boolean> active) {
    var query =
        history.createHistoricCaseInstanceQuery().caseDefinitionKey(PlanItems.definitionKey(type));
    if (active.isPresent()) {
      query = active.get() ? query.unfinished() : query.finished();
    }
    return newestFirst(query);
  }

  @Override
  public List<CaseSnapshot> listForClient(long clientId) {
    var keys =
        Arrays.stream(CaseType.values()).map(PlanItems::definitionKey).collect(Collectors.toSet());
    return newestFirst(
        history
            .createHistoricCaseInstanceQuery()
            .caseInstanceBusinessKey(String.valueOf(clientId))
            .caseDefinitionKeys(keys));
  }

  @Override
  public void perform(String caseId, CaseAction action, String note, Outcome outcome) {
    switch (PlanItems.target(action).kind()) {
      case HUMAN_TASK -> completeTask(caseId, action, note, outcome);
      case USER_EVENT -> triggerUserEvent(caseId, action, note);
    }
    log.info("Performed {} on case {}", action.actionName(), caseId);
  }

  private void completeTask(String caseId, CaseAction action, String note, Outcome outcome) {
    var task =
        tasks.createTaskQuery().caseInstanceId(caseId)
            .taskDefinitionKey(PlanItems.target(action).planItemDefinitionId()).list().stream()
            .findFirst()
            .orElseThrow(() -> new ActionNotAvailableException(action.actionName()));
    Map<String, Object> local = new HashMap<>();
    if (note != null) {
      local.put(CaseVariables.NOTE, note);
    }
    if (outcome != null) {
      local.put(CaseVariables.OUTCOME, outcome.name());
      runtime.setVariable(caseId, CaseVariables.OUTCOME, outcome.name());
    }
    if (!local.isEmpty()) {
      tasks.setVariablesLocal(task.getId(), local);
    }
    tasks.complete(task.getId());
  }

  /**
   * Fires a repeatable user event listener. Flowable keeps the available listener instance and
   * records each occurrence as a new, already ended instance; the note is stored on that one.
   */
  private void triggerUserEvent(String caseId, CaseAction action, String note) {
    var listener =
        listenerInstances(caseId, action).planItemInstanceStateAvailable().list().stream()
            .findFirst()
            .orElseThrow(() -> new ActionNotAvailableException(action.actionName()));
    var previousOccurrences = completedOccurrenceIds(caseId, action);
    runtime.completeUserEventListenerInstance(listener.getId());
    if (note != null) {
      completedOccurrenceIds(caseId, action).stream()
          .filter(id -> !previousOccurrences.contains(id))
          .forEach(id -> runtime.setLocalVariable(id, CaseVariables.NOTE, note));
    }
  }

  private Set<String> completedOccurrenceIds(String caseId, CaseAction action) {
    return listenerInstances(caseId, action).planItemInstanceStateCompleted().includeEnded().list()
        .stream()
        .map(PlanItemInstance::getId)
        .collect(Collectors.toSet());
  }

  private PlanItemInstanceQuery listenerInstances(String caseId, CaseAction action) {
    return runtime
        .createPlanItemInstanceQuery()
        .caseInstanceId(caseId)
        .planItemDefinitionType(PlanItemDefinitionType.USER_EVENT_LISTENER)
        .planItemDefinitionId(PlanItems.target(action).planItemDefinitionId());
  }

  private List<CaseSnapshot> newestFirst(HistoricCaseInstanceQuery query) {
    return query.orderByStartTime().desc().list().stream().map(snapshots::toSnapshot).toList();
  }
}
