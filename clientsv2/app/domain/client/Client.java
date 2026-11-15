package app.domain.client;

import app.domain.cases.Outcome;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A client and its lifecycle rules. Immutable: every transition validates the current status and
 * returns a new {@code Client}.
 */
public record Client(
    Long id, String name, String email, String country, ClientStatus status, Instant createdAt) {

  private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
  private static final Set<ClientStatus> ONBOARDABLE_FROM =
      EnumSet.of(ClientStatus.PROSPECT, ClientStatus.REJECTED, ClientStatus.OFFBOARDED);

  public Client {
    requireText(name, 200, "name");
    requireText(email, 254, "email");
    if (country == null || !COUNTRY.matcher(country).matches()) {
      throw new IllegalArgumentException("country must be an ISO-3166 alpha-2 code");
    }
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** A newly registered client, not yet persisted. */
  public static Client register(String name, String email, String country, Instant now) {
    return new Client(null, name, email, country, ClientStatus.PROSPECT, now);
  }

  public Client withId(long newId) {
    return new Client(newId, name, email, country, status, createdAt);
  }

  public Client startOnboarding() {
    return moveTo(ClientStatus.ONBOARDING, ONBOARDABLE_FROM);
  }

  public Client finishOnboarding(Outcome outcome) {
    var target = outcome == Outcome.APPROVED ? ClientStatus.ACTIVE : ClientStatus.REJECTED;
    return moveTo(target, EnumSet.of(ClientStatus.ONBOARDING));
  }

  public Client startOffboarding() {
    return moveTo(ClientStatus.OFFBOARDING, EnumSet.of(ClientStatus.ACTIVE));
  }

  public Client finishOffboarding() {
    return moveTo(ClientStatus.OFFBOARDED, EnumSet.of(ClientStatus.OFFBOARDING));
  }

  private Client moveTo(ClientStatus target, Set<ClientStatus> allowedFrom) {
    if (!allowedFrom.contains(status)) {
      throw new InvalidClientTransitionException(status, allowedFrom);
    }
    return new Client(id, name, email, country, target, createdAt);
  }

  private static void requireText(String value, int maxLength, String field) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must be 1.." + maxLength + " characters");
    }
  }
}
