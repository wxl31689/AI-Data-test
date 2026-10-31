package com.wxl.demoapi.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotNull(message = "车辆 ID 不能为空")
        @Positive(message = "车辆 ID 必须为正数")
        Long carId,

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        @Max(value = 10, message = "数量不能超过 10")
        Integer quantity
) {
}

