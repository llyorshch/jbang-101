package app.adapter.out.jdbc;

import app.domain.client.Client;
import app.domain.client.ClientStatus;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Persistence mapping of a client. H2 folds the unquoted DDL table name to upper case. */
@Table("CLIENTS")
record ClientRow(
    @Id Long id, String name, String email, String country, String status, Instant createdAt) {

  static ClientRow from(Client client) {
    return new ClientRow(
        client.id(),
        client.name(),
        client.email(),
        client.country(),
        client.status().name(),
        client.createdAt());
  }

  ClientRow withId(Long newId) {
    return new ClientRow(newId, name, email, country, status, createdAt);
  }

  Client toDomain() {
    return new Client(id, name, email, country, ClientStatus.valueOf(status), createdAt);
  }
}
