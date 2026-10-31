package com.wxl.demoapi.order;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId,
        long carId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String status
) {
}

