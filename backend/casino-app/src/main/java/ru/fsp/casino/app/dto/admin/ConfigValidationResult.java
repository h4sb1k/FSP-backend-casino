package ru.fsp.casino.app.dto.admin;

import java.util.List;

public record ConfigValidationResult(
    Boolean valid,
    List<String> errors,
    List<String> warnings,
    Double estimatedHouseEdge,
    Double estimatedOperatorRoi
) {}
