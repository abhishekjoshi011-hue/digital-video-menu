package com.digital.menu.controllers;

import com.digital.menu.dto.DemandForecastResponse;
import com.digital.menu.dto.UpdateOrderStatusRequest;
import com.digital.menu.dto.UpdateOrderItemStatusRequest;
import com.digital.menu.dto.TableStateSummary;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.security.AdminUserPrincipal;
import com.digital.menu.security.TenantContext;
import com.digital.menu.service.OrderServicePort;
import com.digital.menu.service.OrderStreamService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderServicePort orderService;
    private final OrderStreamService orderStreamService;

    public AdminOrderController(OrderServicePort orderService, OrderStreamService orderStreamService) {
        this.orderService = orderService;
        this.orderStreamService = orderStreamService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','CAPTAIN','KITCHEN')")
    public List<RestaurantOrder> getOrders() {
        return orderService.getOrdersForTenant(TenantContext.getTenantIdOrThrow());
    }

    @GetMapping("/table/{tableNumber}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','CAPTAIN','KITCHEN')")
    public List<RestaurantOrder> getOrdersForTable(@PathVariable Integer tableNumber) {
        return orderService.getOrdersForTable(TenantContext.getTenantIdOrThrow(), tableNumber);
    }

    @GetMapping("/kds/queue")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','KITCHEN')")
    public List<RestaurantOrder> getKitchenQueue() {
        return orderService.getKitchenQueue(TenantContext.getTenantIdOrThrow());
    }

    @GetMapping("/tables/states")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','CAPTAIN')")
    public List<TableStateSummary> getTableStates() {
        return orderService.getTableStateSummary(TenantContext.getTenantIdOrThrow());
    }

    @GetMapping("/insights/forecast")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN')")
    public DemandForecastResponse getDemandForecast(
        @RequestParam(defaultValue = "28") int lookbackDays,
        @RequestParam(defaultValue = "7") int horizonDays,
        @RequestParam(defaultValue = "auto") String engine,
        @RequestParam(defaultValue = "xgboost") String model
    ) {
        return orderService.getDemandForecast(
            TenantContext.getTenantIdOrThrow(),
            lookbackDays,
            horizonDays,
            engine,
            model
        );
    }

    @GetMapping("/stream")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','CAPTAIN','KITCHEN')")
    public SseEmitter streamOrders() {
        return orderStreamService.subscribe(TenantContext.getTenantIdOrThrow());
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','CAPTAIN','KITCHEN')")
    public RestaurantOrder updateStatus(
        @PathVariable String orderId,
        @RequestBody UpdateOrderStatusRequest request,
        Authentication authentication
    ) {
        return orderService.updateStatus(
            TenantContext.getTenantIdOrThrow(),
            orderId,
            request.getStatus(),
            ((AdminUserPrincipal) authentication.getPrincipal()).getUsername()
        );
    }

    @PatchMapping("/{orderId}/items/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','ADMIN','KITCHEN')")
    public RestaurantOrder updateItemStatus(
        @PathVariable String orderId,
        @RequestBody UpdateOrderItemStatusRequest request,
        Authentication authentication
    ) {
        return orderService.updateItemStatus(
            TenantContext.getTenantIdOrThrow(),
            orderId,
            request.getDishId(),
            request.getStatus(),
            ((AdminUserPrincipal) authentication.getPrincipal()).getUsername()
        );
    }
}
