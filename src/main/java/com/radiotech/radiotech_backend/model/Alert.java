package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Alert {

    private String id;

    private String operatore;

    private String antennaId;

    private String descrizione;

    /**
     * BASSA
     * MEDIA
     * CRITICA
     */
    private String priorita;

    private String timestamp;

    private boolean letto;
}