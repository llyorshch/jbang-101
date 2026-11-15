package app.application.service;

import app.application.port.in.FindCases;
import app.application.port.in.RenderCaseDiagram;
import app.application.port.out.CaseDiagramRenderer;
import app.application.port.out.CaseEngine;
import app.application.port.out.ClientRepository;
import app.domain.cases.CaseNotFoundException;
import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CaseQueryService implements FindCases, RenderCaseDiagram {

  private final CaseEngine cases;
  private final CaseDiagramRenderer diagrams;
  private final ClientRepository clients;

  CaseQueryService(CaseEngine cases, CaseDiagramRenderer diagrams, ClientRepository clients) {
    this.cases = cases;
    this.diagrams = diagrams;
    this.clients = clients;
  }

  @Override
  @Transactional(readOnly = true)
  public CaseSnapshot byId(CaseType type, String caseId) {
    return cases.find(type, caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CaseSnapshot> list(CaseType type, Optional<Boolean> active) {
    return cases.list(type, active);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CaseSnapshot> ofClient(long clientId) {
    clients.getById(clientId);
    return cases.listForClient(clientId);
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] renderPng(CaseType type, String caseId) {
    return diagrams.renderPng(type, caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
  }
}
