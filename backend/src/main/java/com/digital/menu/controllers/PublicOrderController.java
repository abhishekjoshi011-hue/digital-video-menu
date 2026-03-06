package com.digital.menu.controllers;

import com.digital.menu.dto.PlaceOrderRequest;
import com.digital.menu.dto.PublicPlaceOrderRequest;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.service.OrderServicePort;
import com.digital.menu.service.TableQrServicePort;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicOrderController {
    private final OrderServicePort orderService;
    private final TableQrServicePort tableQrService;

    public PublicOrderController(OrderServicePort orderService, TableQrServicePort tableQrService) {
        this.orderService = orderService;
        this.tableQrService = tableQrService;
    }

    @PostMapping("/orders")
    public RestaurantOrder placeOrderWithQrToken(@RequestBody PublicPlaceOrderRequest request) {
        requireIdempotencyKey(request.getIdempotencyKey());
        var context = tableQrService.validatePublicQrToken(request.getQrToken());
        return orderService.placeOrder(
            context.tenantId(),
            context.tableNumber(),
            request.getItems(),
            request.getIdempotencyKey()
        );
    }

    @PostMapping("/{tenantId}/orders")
    public RestaurantOrder placeOrder(@PathVariable String tenantId, @RequestBody PlaceOrderRequest request) {
        if (request.getQrToken() == null || request.getQrToken().isBlank()) {
            throw new IllegalArgumentException("qrToken is required");
        }
        requireIdempotencyKey(request.getIdempotencyKey());
        var context = tableQrService.validatePublicQrToken(request.getQrToken());
        if (!tenantId.equals(context.tenantId())) {
            throw new IllegalArgumentException("Tenant mismatch for QR token");
        }
        return orderService.placeOrder(
            context.tenantId(),
            context.tableNumber(),
            request.getItems(),
            request.getIdempotencyKey()
        );
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
    }
}
