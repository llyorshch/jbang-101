package app.adapter.in.web.dto;

import app.domain.cases.Outcome;
import jakarta.validation.constraints.Size;

/** Optional body of an action call: an operator note and, for {@code decide}, the outcome. */
public record ActionRequest(@Size(max = 500) String note, Outcome outcome) {

  public static final ActionRequest EMPTY = new ActionRequest(null, null);
}
