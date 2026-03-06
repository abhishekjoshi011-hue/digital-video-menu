package com.digital.menu.repository;

import com.digital.menu.model.TableQrCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TableQrCodeRepository extends MongoRepository<TableQrCode, String> {
    Optional<TableQrCode> findByTenantIdAndTableNumberAndActiveTrue(String tenantId, Integer tableNumber);
    Optional<TableQrCode> findByTenantIdAndTableNumberAndTokenAndActiveTrue(String tenantId, Integer tableNumber, String token);
    List<TableQrCode> findByTenantIdAndActiveTrueOrderByTableNumberAsc(String tenantId);
    List<TableQrCode> findByTenantIdOrderByTableNumberAsc(String tenantId);
    List<TableQrCode> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
