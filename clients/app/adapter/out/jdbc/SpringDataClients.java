package app.adapter.out.jdbc;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface SpringDataClients extends ListCrudRepository<ClientRow, Long> {

  List<ClientRow> findAllByOrderByCreatedAtDescIdDesc();

  /** Compare-and-set of the status. Returns the number of updated rows (0 or 1). */
  @Modifying
  @Query("UPDATE clients SET status = :after WHERE id = :id AND status = :before")
  int updateStatus(long id, String before, String after);

  /** Compare-and-set delete. Returns the number of deleted rows (0 or 1). */
  @Modifying
  @Query("DELETE FROM clients WHERE id = :id AND status = :status")
  int deleteByIdAndStatus(long id, String status);
}
