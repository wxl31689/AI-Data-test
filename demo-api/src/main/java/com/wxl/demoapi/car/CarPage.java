package com.wxl.demoapi.car;

import java.util.List;

public record CarPage(
        List<Car> items,
        long total,
        int page,
        int pageSize
) {
}

