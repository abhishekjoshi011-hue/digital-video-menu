package com.digital.menu.service;

import com.digital.menu.dto.DemandForecastItem;
import com.digital.menu.dto.DemandForecastResponse;
import com.digital.menu.dto.TableStateSummary;
import com.digital.menu.model.Dish;
import com.digital.menu.model.OrderEvent;
import com.digital.menu.model.OrderItem;
import com.digital.menu.model.RestaurantOrder;
import com.digital.menu.repository.DishRepository;
import com.digital.menu.repository.RestaurantOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private final ForecastMlClient forecastMlClient;

    @Value("${app.orders.sla-minutes:20}")
    private long slaMinutes;

    public OrderService(
        RestaurantOrderRepository orderRepository,
        DishRepository dishRepository,
        OrderStreamService orderStreamService,
        ForecastMlClient forecastMlClient
    ) {
        this.orderRepository = orderRepository;
        this.dishRepository = dishRepository;
        this.orderStreamService = orderStreamService;
        this.forecastMlClient = forecastMlClient;
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

    @Override
    public DemandForecastResponse getDemandForecast(String tenantId, int lookbackDays, int horizonDays) {
        return getDemandForecast(tenantId, lookbackDays, horizonDays, "auto", "xgboost");
    }

    @Override
    public DemandForecastResponse getDemandForecast(String tenantId, int lookbackDays, int horizonDays, String engine, String model) {
        int safeLookback = Math.max(7, Math.min(180, lookbackDays));
        int safeHorizon = Math.max(1, Math.min(30, horizonDays));
        String normalizedEngine = normalizeEngine(engine);
        String normalizedModel = normalizeModel(model);

        Instant now = Instant.now();
        Instant from = now.minus(safeLookback, ChronoUnit.DAYS);
        List<RestaurantOrder> history = orderRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(tenantId, from, now);
        List<RestaurantOrder> validOrders = history.stream()
            .filter(order -> order.getCreatedAt() != null)
            .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getStatus()))
            .toList();

        Map<String, Dish> dishById = dishRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));

        if ("ml".equals(normalizedEngine) || "auto".equals(normalizedEngine)) {
            var payload = buildMlPayload(tenantId, safeLookback, safeHorizon, normalizedModel, validOrders, dishById);
            var mlResponse = forecastMlClient.predict(payload);
            if (mlResponse.isPresent()) {
                DemandForecastResponse response = mlResponse.get();
                response.setGeneratedAt(response.getGeneratedAt() == null ? now : response.getGeneratedAt());
                response.setLookbackDays(safeLookback);
                response.setHorizonDays(safeHorizon);
                response.setEngineUsed("ml");
                response.setModelUsed(normalizedModel);
                response.setNotes("ML inference from configured forecasting service.");
                return response;
            }
            if ("ml".equals(normalizedEngine)) {
                DemandForecastResponse unavailable = new DemandForecastResponse();
                unavailable.setGeneratedAt(now);
                unavailable.setLookbackDays(safeLookback);
                unavailable.setHorizonDays(safeHorizon);
                unavailable.setEngineUsed("heuristic");
                unavailable.setModelUsed("trend_baseline");
                unavailable.setNotes("ML service unavailable. Returned heuristic fallback.");
                unavailable.setItemForecasts(new ArrayList<>());
                return buildHeuristicForecast(unavailable, validOrders, dishById, safeLookback, safeHorizon, now);
            }
        }

        DemandForecastResponse response = new DemandForecastResponse();
        response.setGeneratedAt(now);
        response.setLookbackDays(safeLookback);
        response.setHorizonDays(safeHorizon);
        response.setEngineUsed("heuristic");
        response.setModelUsed("trend_baseline");
        response.setNotes("Heuristic forecast using recent demand trend.");
        response.setItemForecasts(new ArrayList<>());
        return buildHeuristicForecast(response, validOrders, dishById, safeLookback, safeHorizon, now);
    }

    private DemandForecastResponse buildHeuristicForecast(
        DemandForecastResponse response,
        List<RestaurantOrder> validOrders,
        Map<String, Dish> dishById,
        int safeLookback,
        int safeHorizon,
        Instant now
    ) {
        int halfWindow = Math.max(1, safeLookback / 2);
        Instant mid = now.minus(halfWindow, ChronoUnit.DAYS);
        long olderOrders = validOrders.stream().filter(order -> order.getCreatedAt().isBefore(mid)).count();
        long recentOrders = validOrders.stream().filter(order -> !order.getCreatedAt().isBefore(mid)).count();

        double rawTrend = recentOrders / (double) Math.max(1, olderOrders);
        double trendFactor = clamp(rawTrend, 0.8, 1.25);

        double totalRevenue = validOrders.stream()
            .flatMap(order -> order.getItems().stream())
            .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
            .sum();
        double avgDailyRevenue = totalRevenue / safeLookback;
        double predictedRevenue = avgDailyRevenue * safeHorizon * trendFactor;

        double avgDailyOrders = validOrders.size() / (double) safeLookback;
        int predictedOrders = (int) Math.max(0, Math.round(avgDailyOrders * safeHorizon * trendFactor));

        Map<String, Integer> qtyByDish = new HashMap<>();
        Map<String, String> nameByDish = new HashMap<>();
        for (RestaurantOrder order : validOrders) {
            for (OrderItem item : order.getItems()) {
                String dishKey = item.getDishId() != null && !item.getDishId().isBlank()
                    ? item.getDishId()
                    : ("name:" + String.valueOf(item.getDishName()).trim().toLowerCase());
                qtyByDish.merge(dishKey, Math.max(0, item.getQuantity()), Integer::sum);
                nameByDish.putIfAbsent(dishKey, item.getDishName());
            }
        }

        List<DemandForecastItem> itemForecasts = qtyByDish.entrySet().stream()
            .map(entry -> {
                String dishKey = entry.getKey();
                int sold = entry.getValue();
                double avgDailyDemand = sold / (double) safeLookback;
                int predictedDemand = (int) Math.max(0, Math.ceil(avgDailyDemand * safeHorizon * trendFactor));
                int safetyStock = Math.max(2, (int) Math.ceil(predictedDemand * 0.2));

                DemandForecastItem item = new DemandForecastItem();
                if (!dishKey.startsWith("name:")) {
                    item.setDishId(dishKey);
                    Dish dish = dishById.get(dishKey);
                    item.setDishName(dish != null ? dish.getName() : nameByDish.getOrDefault(dishKey, "Unknown Dish"));
                    item.setCategory(dish != null ? dish.getCategory() : "Uncategorized");
                } else {
                    item.setDishId(null);
                    item.setDishName(nameByDish.getOrDefault(dishKey, "Unknown Dish"));
                    item.setCategory("Uncategorized");
                }
                item.setSoldLastLookback(sold);
                item.setAvgDailyDemand(round(avgDailyDemand));
                item.setPredictedDemand(predictedDemand);
                item.setRecommendedParStock(predictedDemand + safetyStock);
                item.setRecommendation(getStockRecommendation(predictedDemand));
                return item;
            })
            .sorted(Comparator.comparingInt(DemandForecastItem::getPredictedDemand).reversed())
            .limit(30)
            .toList();

        response.setTrendFactor(round(trendFactor));
        response.setPredictedOrders(predictedOrders);
        response.setPredictedRevenue(round(predictedRevenue));
        response.setItemForecasts(itemForecasts);
        return response;
    }

    private Map<String, Object> buildMlPayload(
        String tenantId,
        int lookbackDays,
        int horizonDays,
        String model,
        List<RestaurantOrder> validOrders,
        Map<String, Dish> dishById
    ) {
        Map<LocalDate, DailyStats> daily = new LinkedHashMap<>();
        Instant now = Instant.now();
        for (int i = lookbackDays - 1; i >= 0; i--) {
            LocalDate date = now.minus(i, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate();
            daily.put(date, new DailyStats());
        }

        for (RestaurantOrder order : validOrders) {
            LocalDate date = order.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            DailyStats stats = daily.computeIfAbsent(date, key -> new DailyStats());
            stats.orders += 1;
            double orderRevenue = order.getItems().stream()
                .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                .sum();
            stats.revenue += orderRevenue;
        }

        List<Map<String, Object>> dailyOrders = daily.entrySet().stream()
            .map(entry -> {
                Map<String, Object> row = new HashMap<>();
                row.put("date", entry.getKey().toString());
                row.put("orders", entry.getValue().orders);
                row.put("revenue", round(entry.getValue().revenue));
                return row;
            })
            .toList();

        Map<String, Integer> soldByDish = new HashMap<>();
        Map<String, String> nameByDish = new HashMap<>();
        for (RestaurantOrder order : validOrders) {
            for (OrderItem item : order.getItems()) {
                String dishKey = item.getDishId() != null && !item.getDishId().isBlank()
                    ? item.getDishId()
                    : ("name:" + String.valueOf(item.getDishName()).trim().toLowerCase());
                soldByDish.merge(dishKey, Math.max(0, item.getQuantity()), Integer::sum);
                nameByDish.putIfAbsent(dishKey, item.getDishName());
            }
        }

        List<Map<String, Object>> dishDemand = soldByDish.entrySet().stream()
            .map(entry -> {
                String dishKey = entry.getKey();
                int sold = entry.getValue();
                Dish dish = dishById.get(dishKey);
                String dishName = dish != null && dish.getName() != null && !dish.getName().isBlank()
                    ? dish.getName()
                    : nameByDish.getOrDefault(dishKey, "Unknown Dish");
                String category = dish != null && dish.getCategory() != null && !dish.getCategory().isBlank()
                    ? dish.getCategory()
                    : "Uncategorized";
                Map<String, Object> row = new HashMap<>();
                row.put("dishId", dishKey.startsWith("name:") ? "" : dishKey);
                row.put("dishName", dishName);
                row.put("category", category);
                row.put("soldLastLookback", sold);
                row.put("avgDailyDemand", round(sold / (double) lookbackDays));
                return row;
            })
            .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("lookbackDays", lookbackDays);
        payload.put("horizonDays", horizonDays);
        payload.put("model", model);
        payload.put("dailyOrders", dailyOrders);
        payload.put("dishDemand", dishDemand);
        payload.put("generatedAt", Instant.now().toString());
        return payload;
    }

    private String normalizeEngine(String engine) {
        String value = String.valueOf(engine).trim().toLowerCase();
        if (Set.of("auto", "ml", "heuristic").contains(value)) {
            return value;
        }
        return "auto";
    }

    private String normalizeModel(String model) {
        String value = String.valueOf(model).trim().toLowerCase().replace('-', '_');
        if (Set.of("xgboost", "lstm", "random_forest").contains(value)) {
            return value;
        }
        return "xgboost";
    }

    private static final class DailyStats {
        int orders;
        double revenue;
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

    private String getStockRecommendation(int predictedDemand) {
        if (predictedDemand >= 40) return "High demand: preload prep and stock.";
        if (predictedDemand >= 15) return "Medium demand: maintain regular prep.";
        return "Low demand: keep lean stock.";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
