package app.adapter.out.jdbc;

import app.application.port.out.ClientRepository;
import app.domain.client.Client;
import app.domain.client.ConcurrentClientUpdateException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Spring Data JDBC implementation of the client port. All SQL is parameterized. */
@Repository
class JdbcClientRepository implements ClientRepository {

  private static final Logger log = LoggerFactory.getLogger(JdbcClientRepository.class);

  private final SpringDataClients rows;

  JdbcClientRepository(SpringDataClients rows) {
    this.rows = rows;
  }

  @Override
  public Optional<Client> findById(long id) {
    return rows.findById(id).map(ClientRow::toDomain);
  }

  @Override
  public List<Client> findAllNewestFirst() {
    return rows.findAllByOrderByCreatedAtDescIdDesc().stream().map(ClientRow::toDomain).toList();
  }

  @Override
  public Client add(Client client) {
    if (client.id() != null) {
      throw new IllegalArgumentException("A new client must not have an id");
    }
    var saved = rows.save(ClientRow.from(client)).toDomain();
    log.info("Client {} created", saved.id());
    return saved;
  }

  /** Only status transitions exist, so only the status column is compared and written. */
  @Override
  public void replace(Client before, Client after) {
    if (!sameClientApartFromStatus(before, after)) {
      throw new IllegalArgumentException("replace only supports status changes of one client");
    }
    var updated = rows.updateStatus(before.id(), before.status().name(), after.status().name());
    if (updated == 0) {
      throw new ConcurrentClientUpdateException(before.id());
    }
    log.info("Client {} moved from {} to {}", before.id(), before.status(), after.status());
  }

  private static boolean sameClientApartFromStatus(Client before, Client after) {
    return before.id() != null
        && Objects.equals(before.id(), after.id())
        && Objects.equals(before.name(), after.name())
        && Objects.equals(before.email(), after.email())
        && Objects.equals(before.country(), after.country())
        && Objects.equals(before.createdAt(), after.createdAt());
  }
}
