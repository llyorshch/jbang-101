package app.adapter.in.web;

import app.adapter.in.web.dto.ClientResponse;
import app.adapter.in.web.dto.NewClientRequest;
import app.application.port.in.DeleteClient;
import app.application.port.in.FindClients;
import app.application.port.in.RegisterClient;
import app.application.port.in.RegisterClientCommand;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/clients", produces = "application/json")
class ClientController {

  private final RegisterClient registerClient;
  private final FindClients findClients;
  private final DeleteClient deleteClient;

  ClientController(
      RegisterClient registerClient, FindClients findClients, DeleteClient deleteClient) {
    this.registerClient = registerClient;
    this.findClients = findClients;
    this.deleteClient = deleteClient;
  }

  @GetMapping
  List<ClientResponse> list() {
    return findClients.all().stream().map(ClientResponse::from).toList();
  }

  @PostMapping(consumes = "application/json")
  ResponseEntity<ClientResponse> create(@Valid @RequestBody NewClientRequest request) {
    var created =
        ClientResponse.from(
            registerClient.register(
                new RegisterClientCommand(request.name(), request.email(), request.country())));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}")
  ClientResponse get(@PathVariable long id) {
    return ClientResponse.from(findClients.byId(id));
  }

  /** Permanent: the client and the history of its ended cases are gone afterwards. */
  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable long id) {
    deleteClient.delete(id);
    return ResponseEntity.noContent().build();
  }
}
