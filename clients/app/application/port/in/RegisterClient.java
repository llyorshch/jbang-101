package app.application.port.in;

import app.domain.client.Client;

/** Registers a new client as PROSPECT. */
public interface RegisterClient {

  Client register(RegisterClientCommand command);
}
