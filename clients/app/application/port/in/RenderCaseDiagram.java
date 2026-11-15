package app.application.port.in;

import app.domain.cases.CaseType;

/** Renders a case diagram (PNG) with the current state highlighted. */
public interface RenderCaseDiagram {

  byte[] renderPng(CaseType type, String caseId);
}
