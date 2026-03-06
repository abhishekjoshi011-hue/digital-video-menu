package com.digital.menu.service;

import com.digital.menu.dto.QrTokenResponse;
import com.digital.menu.errors.ErrorMessages;
import com.digital.menu.model.TableQrCode;
import com.digital.menu.repository.TableQrCodeRepository;
import com.digital.menu.security.QrTokenService;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TableQrService implements TableQrServicePort {
    private final TableQrCodeRepository tableQrCodeRepository;
    private final QrTokenService qrTokenService;

    @Value("${app.public-menu-base-url:http://localhost:3000}")
    private String publicMenuBaseUrl;

    public TableQrService(TableQrCodeRepository tableQrCodeRepository, QrTokenService qrTokenService) {
        this.tableQrCodeRepository = tableQrCodeRepository;
        this.qrTokenService = qrTokenService;
    }

    @Override
    public QrTokenResponse createForTable(String tenantId, Integer tableNumber) {
        if (tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_TABLE_NUMBER);
        }
        TableQrCode existing = tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue(tenantId, tableNumber)
            .orElse(null);
        if (existing != null) {
            throw new IllegalArgumentException(String.format(ErrorMessages.QR_ALREADY_EXISTS_TEMPLATE, tableNumber));
        }
        return generateNew(tenantId, tableNumber);
    }

    @Override
    public QrTokenResponse getOrCreateForTable(String tenantId, Integer tableNumber) {
        if (tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_TABLE_NUMBER);
        }

        TableQrCode existing = tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue(tenantId, tableNumber)
            .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        return generateNew(tenantId, tableNumber);
    }

    @Override
    public QrTokenResponse regenerateForTable(String tenantId, Integer tableNumber) {
        if (tableNumber == null || tableNumber <= 0) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_TABLE_NUMBER);
        }

        tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue(tenantId, tableNumber).ifPresent(existing -> {
            existing.setActive(false);
            existing.setUpdatedAt(Instant.now());
            tableQrCodeRepository.save(existing);
        });

        return generateNew(tenantId, tableNumber);
    }

    @Override
    public void revokeForTable(String tenantId, Integer tableNumber) {
        TableQrCode existing = tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue(tenantId, tableNumber)
            .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.ACTIVE_QR_NOT_FOUND));
        existing.setActive(false);
        existing.setUpdatedAt(Instant.now());
        tableQrCodeRepository.save(existing);
    }

    @Override
    public List<QrTokenResponse> listForTenant(String tenantId) {
        return tableQrCodeRepository.findByTenantIdAndActiveTrueOrderByTableNumberAsc(tenantId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public List<QrTokenResponse> listHistoryForTenant(String tenantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<TableQrCode> sorted = tableQrCodeRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        if (sorted.size() > safeLimit) {
            sorted = sorted.subList(0, safeLimit);
        }
        return sorted.stream().map(this::toResponse).toList();
    }

    @Override
    public QrTokenService.QrContext validatePublicQrToken(String token) {
        QrTokenService.QrContext context = qrTokenService.parseToken(token);
        TableQrCode qrCode = tableQrCodeRepository
            .findByTenantIdAndTableNumberAndTokenAndActiveTrue(context.tenantId(), context.tableNumber(), token)
            .orElseThrow(() -> new IllegalArgumentException("QR token is invalid or revoked"));

        return context;
    }

    private QrTokenResponse generateNew(String tenantId, Integer tableNumber) {
        Instant now = Instant.now();
        String token = qrTokenService.generateToken(tenantId, tableNumber);
        TableQrCode qrCode = new TableQrCode();
        qrCode.setTenantId(tenantId);
        qrCode.setTableNumber(tableNumber);
        qrCode.setToken(token);
        qrCode.setMenuUrl(publicMenuBaseUrl + "/?t=" + token);
        qrCode.setActive(true);
        qrCode.setCreatedAt(now);
        qrCode.setUpdatedAt(now);
        return toResponse(tableQrCodeRepository.save(qrCode));
    }

    private QrTokenResponse toResponse(TableQrCode qrCode) {
        return new QrTokenResponse(
            qrCode.getToken(),
            qrCode.getMenuUrl(),
            qrCode.getTenantId(),
            qrCode.getTableNumber(),
            qrCode.isActive(),
            qrCode.getCreatedAt(),
            qrCode.getUpdatedAt()
        );
    }
}
