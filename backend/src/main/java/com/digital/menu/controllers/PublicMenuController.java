package com.digital.menu.controllers;

import com.digital.menu.model.Dish;
import com.digital.menu.service.DishService;
import com.digital.menu.service.TableQrServicePort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicMenuController {
    private final DishService dishService;
    private final TableQrServicePort tableQrService;

    public PublicMenuController(DishService dishService, TableQrServicePort tableQrService) {
        this.dishService = dishService;
        this.tableQrService = tableQrService;
    }

    @GetMapping("/menu")
    public List<Dish> getMenuForQrToken(@RequestParam("t") String token) {
        var context = tableQrService.validatePublicQrToken(token);
        return dishService.getAllDishes(context.tenantId());
    }

    @GetMapping("/{tenantId}/menu")
    public List<Dish> getTenantMenu(@PathVariable String tenantId) {
        return dishService.getAllDishes(tenantId);
    }
}
