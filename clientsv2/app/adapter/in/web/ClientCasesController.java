package app.adapter.in.web;

import app.adapter.in.web.dto.CaseResponse;
import app.application.port.in.FindCases;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Lists every case (onboarding and offboarding) of one client. */
@RestController
class ClientCasesController {

  private final FindCases findCases;

  ClientCasesController(FindCases findCases) {
    this.findCases = findCases;
  }

  @GetMapping(path = "/api/clients/{id}/cases", produces = "application/json")
  List<CaseResponse> list(@PathVariable long id) {
    return findCases.ofClient(id).stream().map(CaseResponse::from).toList();
  }
}
