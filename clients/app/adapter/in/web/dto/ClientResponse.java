package app.adapter.in.web.dto;

import app.domain.client.Client;
import app.domain.client.ClientStatus;
import java.time.Instant;

/** Client as exposed by the REST API. */
public record ClientResponse(
    long id, String name, String email, String country, ClientStatus status, Instant createdAt) {

  public static ClientResponse from(Client client) {
    return new ClientResponse(
        client.id(),
        client.name(),
        client.email(),
        client.country(),
        client.status(),
        client.createdAt());
  }
}
