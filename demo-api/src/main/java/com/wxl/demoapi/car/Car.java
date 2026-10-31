package com.wxl.demoapi.car;

import java.math.BigDecimal;

public record Car(
        long id,
        String brand,
        String model,
        int year,
        BigDecimal price,
        long mileageKm
) {
}

