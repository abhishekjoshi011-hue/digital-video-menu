package com.digital.menu.repository;

import com.digital.menu.model.AdminUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminUserRepository extends MongoRepository<AdminUser, String> {
    Optional<AdminUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<AdminUser> findByTenantId(String tenantId);
}
