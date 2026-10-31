package com.wxl.demoapi.car;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/demo/cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping("/{id}")
    public Car detail(@PathVariable @Positive(message = "车辆 ID 必须为正数") long id) {
        return carService.findById(id);
    }

    @GetMapping
    public CarPage search(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) @Min(value = 0, message = "最低价格不能为负数") BigDecimal minPrice,
            @RequestParam(required = false) @Min(value = 0, message = "最高价格不能为负数") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页数量至少为 1")
            @Max(value = 100, message = "每页数量不能超过 100") int pageSize
    ) {
        return carService.search(brand, minPrice, maxPrice, page, pageSize);
    }
}

