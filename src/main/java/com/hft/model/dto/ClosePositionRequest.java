package com.hft.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/** "I sold this" — the exit price the user actually got on their brokerage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosePositionRequest {

    @NotNull @DecimalMin(value = "0.0001")
    private BigDecimal exitPrice;
}
