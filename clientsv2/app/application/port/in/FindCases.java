package app.application.port.in;

import app.domain.cases.CaseSnapshot;
import app.domain.cases.CaseType;
import java.util.List;
import java.util.Optional;

/** Reads running and ended cases. */
public interface FindCases {

  CaseSnapshot byId(CaseType type, String caseId);

  /** Newest first; an empty {@code active} means running and ended cases. */
  List<CaseSnapshot> list(CaseType type, Optional<Boolean> active);

  /** All cases of an existing client, newest first. */
  List<CaseSnapshot> ofClient(long clientId);
}
