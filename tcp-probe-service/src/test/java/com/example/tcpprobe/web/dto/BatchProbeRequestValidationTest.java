package com.example.tcpprobe.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class BatchProbeRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectNullTargetElement() {
        BatchProbeRequest request = new BatchProbeRequest(
                Collections.singletonList(null),
                1000
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("目标元素不能为 null"));
    }
}
