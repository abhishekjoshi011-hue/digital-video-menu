package com.digital.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digital.menu.model.Dish;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.repository.DishRepository;
import com.digital.menu.repository.RestaurantOrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private DishRepository dishRepository;

    @Mock
    private OrderStreamService orderStreamService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, dishRepository, orderStreamService);
        ReflectionTestUtils.setField(orderService, "slaMinutes", 20L);
    }

    @Test
    void placeOrder_requiresIdempotencyKey() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> orderService.placeOrder("tenant-a", 1, List.of(validItem("dish-1")), null)
        );
        assertEquals("idempotencyKey is required", ex.getMessage());
    }

    @Test
    void placeOrder_duplicateInsertRace_returnsExistingOrder() {
        String tenantId = "tenant-a";
        Integer tableNumber = 1;
        String idempotencyKey = "idem-123";

        Dish dish = new Dish();
        dish.setId("dish-1");
        dish.setTenantId(tenantId);
        dish.setName("Soup");
        dish.setPrice(9.5);
        dish.setCategory("Starter");

        RestaurantOrder existing = new RestaurantOrder();
        existing.setId("order-existing");
        existing.setTenantId(tenantId);
        existing.setTableNumber(tableNumber);
        existing.setIdempotencyKey(idempotencyKey);

        when(orderRepository.findFirstByTenantIdAndTableNumberAndIdempotencyKeyOrderByCreatedAtDesc(
            tenantId, tableNumber, idempotencyKey
        ))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        when(dishRepository.findByTenantIdAndIdIn(eq(tenantId), anyList())).thenReturn(List.of(dish));
        when(orderRepository.save(any(RestaurantOrder.class))).thenThrow(new DuplicateKeyException("dup"));

        RestaurantOrder actual = orderService.placeOrder(
            tenantId,
            tableNumber,
            List.of(validItem("dish-1")),
            idempotencyKey
        );

        assertEquals("order-existing", actual.getId());
        verify(orderRepository, times(2))
            .findFirstByTenantIdAndTableNumberAndIdempotencyKeyOrderByCreatedAtDesc(tenantId, tableNumber, idempotencyKey);
    }

    private OrderItem validItem(String dishId) {
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setQuantity(1);
        return item;
    }
}
