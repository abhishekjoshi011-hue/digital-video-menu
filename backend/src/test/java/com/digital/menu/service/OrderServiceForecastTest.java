package com.digital.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.digital.menu.dto.DemandForecastResponse;
import com.digital.menu.model.Dish;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.repository.DishRepository;
import com.digital.menu.repository.RestaurantOrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceForecastTest {

    @Mock
    private RestaurantOrderRepository orderRepository;
    @Mock
    private DishRepository dishRepository;
    @Mock
    private OrderStreamService orderStreamService;
    @Mock
    private ForecastMlClient forecastMlClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, dishRepository, orderStreamService, forecastMlClient);
        ReflectionTestUtils.setField(orderService, "slaMinutes", 20L);
    }

    @Test
    void getDemandForecast_mlEngineUsesMlResponseWhenAvailable() {
        when(orderRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
            .thenReturn(List.of(sampleOrder("dish-1", 2, 100, 2)));
        when(dishRepository.findByTenantId("tenant-a")).thenReturn(List.of(sampleDish("dish-1", "Noodles", "Main")));

        DemandForecastResponse ml = new DemandForecastResponse();
        ml.setPredictedOrders(50);
        ml.setPredictedRevenue(999.0);
        ml.setTrendFactor(1.21);
        when(forecastMlClient.predict(any())).thenReturn(Optional.of(ml));

        DemandForecastResponse result = orderService.getDemandForecast("tenant-a", 28, 7, "ml", "xgboost");

        assertEquals("ml", result.getEngineUsed());
        assertEquals("xgboost", result.getModelUsed());
        assertEquals(50, result.getPredictedOrders());
        assertEquals(999.0, result.getPredictedRevenue());
    }

    @Test
    void getDemandForecast_mlFallsBackToHeuristicWhenUnavailable() {
        when(orderRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
            .thenReturn(List.of(sampleOrder("dish-1", 3, 120, 1), sampleOrder("dish-1", 2, 120, 8)));
        when(dishRepository.findByTenantId("tenant-a")).thenReturn(List.of(sampleDish("dish-1", "Noodles", "Main")));
        when(forecastMlClient.predict(any())).thenReturn(Optional.empty());

        DemandForecastResponse result = orderService.getDemandForecast("tenant-a", 28, 7, "ml", "lstm");

        assertEquals("heuristic", result.getEngineUsed());
        assertEquals("trend_baseline", result.getModelUsed());
        assertTrue(result.getPredictedOrders() >= 0);
        assertFalse(result.getItemForecasts().isEmpty());
    }

    @Test
    void getDemandForecast_heuristicEngineIgnoresMlClient() {
        when(orderRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
            .thenReturn(List.of(sampleOrder("dish-1", 4, 140, 2), sampleOrder("dish-2", 1, 80, 3)));
        when(dishRepository.findByTenantId("tenant-a"))
            .thenReturn(List.of(sampleDish("dish-1", "Noodles", "Main"), sampleDish("dish-2", "Tea", "Beverage")));

        DemandForecastResponse result = orderService.getDemandForecast("tenant-a", 14, 7, "heuristic", "random_forest");

        assertEquals("heuristic", result.getEngineUsed());
        assertEquals("trend_baseline", result.getModelUsed());
        assertTrue(result.getPredictedRevenue() >= 0);
        assertFalse(result.getItemForecasts().isEmpty());
    }

    private RestaurantOrder sampleOrder(String dishId, int qty, double price, int daysAgo) {
        RestaurantOrder order = new RestaurantOrder();
        order.setTenantId("tenant-a");
        order.setTableNumber(1);
        order.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        order.setStatus("PLACED");
        OrderItem item = new OrderItem();
        item.setDishId(dishId);
        item.setDishName("Dish-" + dishId);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        order.setItems(List.of(item));
        return order;
    }

    private Dish sampleDish(String id, String name, String category) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setTenantId("tenant-a");
        dish.setName(name);
        dish.setCategory(category);
        dish.setPrice(100);
        return dish;
    }
}

