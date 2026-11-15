package app.application.port.out;

import app.domain.cases.CaseType;
import java.util.Optional;

/** Renders a case as a PNG image with its current state highlighted. */
public interface CaseDiagramRenderer {

  /** Empty when the case does not exist for this type or its model has no diagram layout. */
  Optional<byte[]> renderPng(CaseType type, String caseId);
}
