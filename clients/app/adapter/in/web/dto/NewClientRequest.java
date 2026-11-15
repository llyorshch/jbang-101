package app.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for {@code POST /api/clients}. */
public record NewClientRequest(
    @NotBlank @Size(max = 200) String name,
    @Email @NotBlank @Size(max = 254) String email,
    @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO-3166 alpha-2 code")
        String country) {}
