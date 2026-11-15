package app.application.port.in;

/** Data needed to register a client. Format rules are enforced again by the domain. */
public record RegisterClientCommand(String name, String email, String country) {}
