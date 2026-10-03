package com.radiotech.radiotech_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RicambioDto {
    @NotBlank
    @Size(max = 80)
    private String codiceSku;

    @NotBlank
    @Size(max = 160)
    private String nomePezzo;

    @Min(0)
    private int quantitaDisponibile;

    @Min(0)
    private int sogliaMinima;

    @NotBlank
    @Size(max = 80)
    private String categoria;
}
