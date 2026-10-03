package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Ricambio {

    private String id;

    private String codiceSku;

    private String nomePezzo;

    private int quantitaDisponibile;

    private int sogliaMinima;

    private String categoria;

    private String createdAt;

    private String updatedAt;
}