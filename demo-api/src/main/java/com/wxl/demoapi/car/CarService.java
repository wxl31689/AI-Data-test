package com.wxl.demoapi.car;

import com.wxl.demoapi.common.DemoApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class CarService {

    private static final List<Car> CARS = List.of(
            new Car(1001, "比亚迪", "宋 PLUS", 2024, new BigDecimal("159800"), 12000),
            new Car(1002, "特斯拉", "Model 3", 2023, new BigDecimal("231900"), 18500),
            new Car(1003, "小米", "SU7", 2025, new BigDecimal("215900"), 3200),
            new Car(1004, "吉利", "星越 L", 2022, new BigDecimal("137200"), 28600),
            new Car(1005, "蔚来", "ET5", 2024, new BigDecimal("298000"), 8600)
    );

    public Car findById(long id) {
        return CARS.stream()
                .filter(car -> car.id() == id)
                .findFirst()
                .orElseThrow(() -> new DemoApiException(
                        HttpStatus.NOT_FOUND,
                        "CAR_NOT_FOUND",
                        "车辆不存在"
                ));
    }

    public CarPage search(
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int pageSize
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new DemoApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PRICE_RANGE",
                    "最低价格不能大于最高价格"
            );
        }

        String normalizedBrand = brand == null ? null : brand.trim().toLowerCase(Locale.ROOT);
        List<Car> filtered = CARS.stream()
                .filter(car -> normalizedBrand == null || normalizedBrand.isBlank()
                        || car.brand().toLowerCase(Locale.ROOT).contains(normalizedBrand))
                .filter(car -> minPrice == null || car.price().compareTo(minPrice) >= 0)
                .filter(car -> maxPrice == null || car.price().compareTo(maxPrice) <= 0)
                .toList();

        int start = Math.min(page * pageSize, filtered.size());
        int end = Math.min(start + pageSize, filtered.size());
        return new CarPage(filtered.subList(start, end), filtered.size(), page, pageSize);
    }
}

