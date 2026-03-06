package com.digital.menu.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digital.menu.dto.PlaceOrderRequest;
import com.digital.menu.dto.PublicPlaceOrderRequest;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.security.QrTokenService;
import com.digital.menu.service.OrderServicePort;
import com.digital.menu.service.TableQrServicePort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicOrderControllerTest {

    @Mock
    private OrderServicePort orderService;
    @Mock
    private TableQrServicePort tableQrService;

    private PublicOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicOrderController(orderService, tableQrService);
    }

    @Test
    void placeOrderWithQrToken_requiresIdempotencyKey() {
        PublicPlaceOrderRequest req = new PublicPlaceOrderRequest();
        req.setQrToken("token-1");
        req.setItems(List.of(item("dish-1")));
        req.setIdempotencyKey("");

        assertThrows(IllegalArgumentException.class, () -> controller.placeOrderWithQrToken(req));
    }

    @Test
    void placeOrderWithTenantPath_rejectsTenantMismatch() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setQrToken("token-2");
        req.setIdempotencyKey("idem-1");
        req.setItems(List.of(item("dish-1")));

        when(tableQrService.validatePublicQrToken("token-2"))
            .thenReturn(new QrTokenService.QrContext("tenant-a", 11));

        assertThrows(IllegalArgumentException.class, () -> controller.placeOrder("tenant-b", req));
    }

    @Test
    void placeOrderWithQrToken_callsOrderServiceUsingQrContext() {
        PublicPlaceOrderRequest req = new PublicPlaceOrderRequest();
        req.setQrToken("token-3");
        req.setIdempotencyKey("idem-2");
        req.setItems(List.of(item("dish-1")));

        RestaurantOrder created = new RestaurantOrder();
        created.setTenantId("tenant-a");
        created.setTableNumber(8);

        when(tableQrService.validatePublicQrToken("token-3"))
            .thenReturn(new QrTokenService.QrContext("tenant-a", 8));
        when(orderService.placeOrder(eq("tenant-a"), eq(8), anyList(), eq("idem-2"))).thenReturn(created);

        RestaurantOrder response = controller.placeOrderWithQrToken(req);

        assertEquals("tenant-a", response.getTenantId());
        assertEquals(8, response.getTableNumber());
        verify(orderService).placeOrder(eq("tenant-a"), eq(8), anyList(), eq("idem-2"));
    }

    private OrderItem item(String dishId) {
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setQuantity(1);
        return item;
    }
}

