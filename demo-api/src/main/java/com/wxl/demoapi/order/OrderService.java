package com.wxl.demoapi.order;

import com.wxl.demoapi.car.Car;
import com.wxl.demoapi.car.CarService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private final CarService carService;

    public OrderService(CarService carService) {
        this.carService = carService;
    }

    public OrderResponse create(CreateOrderRequest request) {
        Car car = carService.findById(request.carId());
        return new OrderResponse(
                "ORD-" + UUID.randomUUID(),
                car.id(),
                request.quantity(),
                car.price(),
                car.price().multiply(java.math.BigDecimal.valueOf(request.quantity())),
                "CREATED"
        );
    }
}

