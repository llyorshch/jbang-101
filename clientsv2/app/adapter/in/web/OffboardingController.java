package app.adapter.in.web;

import app.application.port.in.CaseActionCommand;
import app.application.port.in.FindCases;
import app.application.port.in.PerformCaseAction;
import app.application.port.in.RenderCaseDiagram;
import app.application.port.in.StartOffboarding;
import app.adapter.in.web.dto.ActionRequest;
import app.adapter.in.web.dto.CaseResponse;
import app.domain.cases.CaseType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class OffboardingController {

  private static final CaseType TYPE = CaseType.OFFBOARDING;

  private final StartOffboarding startOffboarding;
  private final FindCases findCases;
  private final PerformCaseAction performAction;
  private final RenderCaseDiagram renderDiagram;

  OffboardingController(
      StartOffboarding startOffboarding,
      FindCases findCases,
      PerformCaseAction performAction,
      RenderCaseDiagram renderDiagram) {
    this.startOffboarding = startOffboarding;
    this.findCases = findCases;
    this.performAction = performAction;
    this.renderDiagram = renderDiagram;
  }

  @PostMapping(path = "/api/clients/{clientId}/offboardings", produces = "application/json")
  ResponseEntity<CaseResponse> start(@PathVariable long clientId) {
    var started = CaseResponse.from(startOffboarding.startOffboarding(clientId));
    var location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/offboardings/{caseId}")
            .buildAndExpand(started.id())
            .toUri();
    return ResponseEntity.created(location).body(started);
  }

  @GetMapping(path = "/api/offboardings", produces = "application/json")
  List<CaseResponse> list(@RequestParam(required = false) Boolean active) {
    return findCases.list(TYPE, Optional.ofNullable(active)).stream().map(CaseResponse::from).toList();
  }

  @GetMapping(path = "/api/offboardings/{caseId}", produces = "application/json")
  CaseResponse get(@PathVariable String caseId) {
    return CaseResponse.from(findCases.byId(TYPE, caseId));
  }

  @PostMapping(path = "/api/offboardings/{caseId}/actions/{action}", produces = "application/json")
  CaseResponse perform(
      @PathVariable String caseId,
      @PathVariable String action,
      @Valid @RequestBody(required = false) ActionRequest request) {
    var body = request == null ? ActionRequest.EMPTY : request;
    return CaseResponse.from(
        performAction.perform(
            new CaseActionCommand(TYPE, caseId, action, body.note(), body.outcome())));
  }

  @GetMapping("/api/offboardings/{caseId}/diagram")
  ResponseEntity<byte[]> diagram(@PathVariable String caseId) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .body(renderDiagram.renderPng(TYPE, caseId));
  }
}
