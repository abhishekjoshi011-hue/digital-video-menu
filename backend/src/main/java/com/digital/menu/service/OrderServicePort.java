package com.digital.menu.service;

import com.digital.menu.dto.DemandForecastResponse;
import com.digital.menu.dto.TableStateSummary;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import java.util.List;

public interface OrderServicePort {
    RestaurantOrder placeOrder(String tenantId, Integer tableNumber, List<OrderItem> items, String idempotencyKey);
    List<RestaurantOrder> getOrdersForTenant(String tenantId);
    List<RestaurantOrder> getOrdersForTable(String tenantId, Integer tableNumber);
    List<RestaurantOrder> getKitchenQueue(String tenantId);
    RestaurantOrder updateStatus(String tenantId, String orderId, String status, String actor);
    RestaurantOrder updateItemStatus(String tenantId, String orderId, String dishId, String status, String actor);
    List<TableStateSummary> getTableStateSummary(String tenantId);
    DemandForecastResponse getDemandForecast(String tenantId, int lookbackDays, int horizonDays);
    DemandForecastResponse getDemandForecast(String tenantId, int lookbackDays, int horizonDays, String engine, String model);
}
