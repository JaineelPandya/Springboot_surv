package com.surv.platform.document.dto;

import com.surv.platform.domain.Document;
import jakarta.validation.constraints.NotNull;

public record ClassifyRequest(
        @NotNull Document.SensitivityLevel newLevel,
        String reason
) {}
