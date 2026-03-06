package com.digital.menu.repository;

import com.digital.menu.model.Dish;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishRepository extends MongoRepository<Dish, String> {
    List<Dish> findByTenantId(String tenantId);
    List<Dish> findByTenantIdAndIdIn(String tenantId, List<String> ids);
    Optional<Dish> findByTenantIdAndName(String tenantId, String name);
    void deleteByTenantIdAndNameIn(String tenantId, List<String> names);
}
