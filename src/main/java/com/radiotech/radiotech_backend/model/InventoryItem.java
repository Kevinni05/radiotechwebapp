package com.radiotech.radiotech_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    private String id;
    private String sku;
    private String name;
    private String category;
    private Integer quantity;
    private Integer minimumThreshold;
    private String createdAt;
    private String updatedAt;
}