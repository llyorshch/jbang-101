package app.application.port.in;

import app.domain.cases.CaseType;
import app.domain.cases.Outcome;

/**
 * An operator action on a case. {@code actionName} is untrusted input and is resolved against the
 * action whitelist; {@code note} and {@code outcome} are optional.
 */
public record CaseActionCommand(
    CaseType type, String caseId, String actionName, String note, Outcome outcome) {}
