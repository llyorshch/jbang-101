package app.domain.client;

/** Lifecycle status of a client. New clients start as {@link #PROSPECT}. */
public enum ClientStatus {
  PROSPECT,
  ONBOARDING,
  ACTIVE,
  REJECTED,
  OFFBOARDING,
  OFFBOARDED
}
