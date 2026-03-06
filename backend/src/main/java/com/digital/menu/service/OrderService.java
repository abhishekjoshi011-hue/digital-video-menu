package com.digital.menu.service;

import com.digital.menu.dto.TableStateSummary;
import com.digital.menu.model.Dish;
import com.digital.menu.model.OrderEvent;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.repository.DishRepository;
import com.digital.menu.repository.RestaurantOrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService implements OrderServicePort {
    private static final Set<String> OPEN_STATUSES = Set.of("PLACED", "PREPARING", "READY", "SERVED");

    private final RestaurantOrderRepository orderRepository;
    private final DishRepository dishRepository;
    private final OrderStreamService orderStreamService;

    @Value("${app.orders.sla-minutes:20}")
    private long slaMinutes;

    public OrderService(
        RestaurantOrderRepository orderRepository,
        DishRepository dishRepository,
        OrderStreamService orderStreamService
    ) {
        this.orderRepository = orderRepository;
        this.dishRepository = dishRepository;
        this.orderStreamService = orderStreamService;
    }

    @Override
    public RestaurantOrder placeOrder(String tenantId, Integer tableNumber, List<OrderItem> items, String idempotencyKey) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException("tableNumber must be greater than 0");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }

        var existing = orderRepository.findFirstByTenantIdAndTableNumberAndIdempotencyKeyOrderByCreatedAtDesc(
            tenantId,
            tableNumber,
            idempotencyKey
        );
        if (existing.isPresent()) {
            return markDelay(existing.get());
        }

        List<OrderItem> sanitizedItems = validateAndNormalizeItems(tenantId, items);
        Instant now = Instant.now();

        RestaurantOrder order = new RestaurantOrder();
        order.setTenantId(tenantId);
        order.setTableNumber(tableNumber);
        order.setItems(sanitizedItems);
        order.setStatus("PLACED");
        order.setTableState("ORDERING");
        order.setSource("QR");
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpectedReadyAt(now.plus(slaMinutes, ChronoUnit.MINUTES));
        order.setDelayed(false);
        appendAudit(order, "ORDER_PLACED", "customer", "Order created from table " + tableNumber);

        try {
            RestaurantOrder saved = orderRepository.save(order);
            orderStreamService.publishOrderCreated(tenantId, saved);
            return saved;
        } catch (DuplicateKeyException ex) {
            return orderRepository
                .findFirstByTenantIdAndTableNumberAndIdempotencyKeyOrderByCreatedAtDesc(
                    tenantId,
                    tableNumber,
                    idempotencyKey
                )
                .map(this::markDelay)
                .orElseThrow(() -> ex);
        }
    }

    @Override
    public List<RestaurantOrder> getOrdersForTenant(String tenantId) {
        return orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            .stream()
            .map(this::markDelay)
            .toList();
    }

    @Override
    public List<RestaurantOrder> getOrdersForTable(String tenantId, Integer tableNumber) {
        return orderRepository.findByTenantIdAndTableNumberOrderByCreatedAtDesc(tenantId, tableNumber)
            .stream()
            .map(this::markDelay)
            .toList();
    }

    @Override
    public List<RestaurantOrder> getKitchenQueue(String tenantId) {
        return orderRepository.findByTenantIdAndStatusInOrderByCreatedAtAsc(
                tenantId,
                List.of("PLACED", "PREPARING", "READY")
            )
            .stream()
            .map(this::markDelay)
            .toList();
    }

    @Override
    public RestaurantOrder updateStatus(String tenantId, String orderId, String status, String actor) {
        RestaurantOrder order = getTenantOrder(tenantId, orderId);

        String normalizedStatus = normalizeStatus(status);
        order.setStatus(normalizedStatus);
        order.setTableState(resolveTableState(normalizedStatus));
        order.setUpdatedAt(Instant.now());

        if ("PREPARING".equals(normalizedStatus)) {
            for (OrderItem item : order.getItems()) {
                if ("PLACED".equals(item.getItemStatus())) {
                    item.setItemStatus("PREPARING");
                }
            }
        } else if ("READY".equals(normalizedStatus) || "SERVED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
            for (OrderItem item : order.getItems()) {
                item.setItemStatus(normalizedStatus);
            }
        }

        appendAudit(order, "STATUS_UPDATED", actor, "Order status -> " + normalizedStatus);
        RestaurantOrder saved = orderRepository.save(order);
        orderStreamService.publishOrderUpdated(tenantId, saved);
        return saved;
    }

    @Override
    public RestaurantOrder updateItemStatus(String tenantId, String orderId, String dishId, String status, String actor) {
        RestaurantOrder order = getTenantOrder(tenantId, orderId);
        String normalizedStatus = normalizeItemStatus(status);

        boolean updated = false;
        for (OrderItem item : order.getItems()) {
            if (dishId != null && dishId.equals(item.getDishId())) {
                item.setItemStatus(normalizedStatus);
                updated = true;
            }
        }
        if (!updated) {
            throw new IllegalArgumentException("Dish item not found in order");
        }

        order.setStatus(resolveOrderStatusFromItems(order.getItems()));
        order.setTableState(resolveTableState(order.getStatus()));
        order.setUpdatedAt(Instant.now());
        appendAudit(order, "ITEM_STATUS_UPDATED", actor, "Dish " + dishId + " -> " + normalizedStatus);
        RestaurantOrder saved = orderRepository.save(order);
        orderStreamService.publishOrderUpdated(tenantId, saved);
        return saved;
    }

    @Override
    public List<TableStateSummary> getTableStateSummary(String tenantId) {
        Map<Integer, List<RestaurantOrder>> byTable = new LinkedHashMap<>();
        for (RestaurantOrder order : orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            byTable.computeIfAbsent(order.getTableNumber(), key -> new ArrayList<>()).add(order);
        }

        return byTable.entrySet().stream().map(entry -> {
            int openOrders = (int) entry.getValue().stream().filter(order -> OPEN_STATUSES.contains(order.getStatus())).count();
            String state = openOrders == 0 ? "AVAILABLE" : resolveTableState(entry.getValue().get(0).getStatus());
            return new TableStateSummary(entry.getKey(), state, openOrders);
        }).toList();
    }

    private RestaurantOrder getTenantOrder(String tenantId, String orderId) {
        RestaurantOrder order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!tenantId.equals(order.getTenantId())) {
            throw new IllegalArgumentException("Order not found for tenant");
        }
        return order;
    }

    private List<OrderItem> validateAndNormalizeItems(String tenantId, List<OrderItem> requestedItems) {
        List<String> ids = requestedItems.stream()
            .map(OrderItem::getDishId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Each order item must include dishId");
        }

        Map<String, Dish> dishById = dishRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
            .collect(Collectors.toMap(Dish::getId, dish -> dish));

        List<OrderItem> normalized = new ArrayList<>();
        for (OrderItem item : requestedItems) {
            Dish dish = dishById.get(item.getDishId());
            if (dish == null) {
                throw new IllegalArgumentException("Invalid dish in order: " + item.getDishId());
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0 for dish " + item.getDishId());
            }

            OrderItem clean = new OrderItem();
            clean.setDishId(dish.getId());
            clean.setDishName(dish.getName());
            clean.setQuantity(item.getQuantity());
            clean.setUnitPrice(dish.getPrice());
            clean.setSelectedAddOns(item.getSelectedAddOns() == null ? new ArrayList<>() : new ArrayList<>(item.getSelectedAddOns()));
            clean.setItemStatus("PLACED");
            clean.setKitchenStation(inferStation(dish));
            normalized.add(clean);
        }
        return normalized;
    }

    private String inferStation(Dish dish) {
        String category = String.valueOf(dish.getCategory()).toLowerCase();
        String name = String.valueOf(dish.getName()).toLowerCase();
        if (category.contains("beverage") || name.contains("coffee") || name.contains("tea")) {
            return "BAR";
        }
        if (category.contains("dessert") || name.contains("ice cream") || name.contains("cake")) {
            return "DESSERT";
        }
        return "MAIN";
    }

    private String normalizeStatus(String status) {
        String value = String.valueOf(status).trim().toUpperCase();
        if (!Set.of("PLACED", "PREPARING", "READY", "SERVED", "CANCELLED", "CLOSED").contains(value)) {
            throw new IllegalArgumentException("Invalid status");
        }
        return value;
    }

    private String normalizeItemStatus(String status) {
        String value = String.valueOf(status).trim().toUpperCase();
        if (!Set.of("PLACED", "PREPARING", "READY", "SERVED", "CANCELLED").contains(value)) {
            throw new IllegalArgumentException("Invalid item status");
        }
        return value;
    }

    private String resolveOrderStatusFromItems(List<OrderItem> items) {
        boolean anyPreparing = items.stream().anyMatch(item -> "PREPARING".equals(item.getItemStatus()));
        boolean anyPlaced = items.stream().anyMatch(item -> "PLACED".equals(item.getItemStatus()));
        boolean allReady = items.stream().allMatch(item -> "READY".equals(item.getItemStatus()));
        boolean allServed = items.stream().allMatch(item -> "SERVED".equals(item.getItemStatus()));
        boolean allCancelled = items.stream().allMatch(item -> "CANCELLED".equals(item.getItemStatus()));

        if (allCancelled) return "CANCELLED";
        if (allServed) return "SERVED";
        if (allReady) return "READY";
        if (anyPreparing) return "PREPARING";
        if (anyPlaced) return "PLACED";
        return "PREPARING";
    }

    private String resolveTableState(String orderStatus) {
        return switch (orderStatus) {
            case "PLACED", "PREPARING" -> "ORDERING";
            case "READY" -> "READY_TO_SERVE";
            case "SERVED" -> "DINING";
            case "CLOSED" -> "CLOSED";
            default -> "AVAILABLE";
        };
    }

    private void appendAudit(RestaurantOrder order, String action, String actor, String note) {
        OrderEvent event = new OrderEvent();
        event.setAction(action);
        event.setActor(actor == null || actor.isBlank() ? "system" : actor);
        event.setAt(Instant.now());
        event.setNote(note);
        order.getAuditTrail().add(event);
    }

    private RestaurantOrder markDelay(RestaurantOrder order) {
        if (order.getExpectedReadyAt() != null
            && Instant.now().isAfter(order.getExpectedReadyAt())
            && Set.of("PLACED", "PREPARING").contains(order.getStatus())) {
            order.setDelayed(true);
        } else {
            order.setDelayed(false);
        }
        return order;
    }

}
