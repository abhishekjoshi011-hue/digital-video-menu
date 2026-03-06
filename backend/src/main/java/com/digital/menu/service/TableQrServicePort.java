package com.digital.menu.service;

import com.digital.menu.dto.QrTokenResponse;
import com.digital.menu.security.QrTokenService;
import java.util.List;

public interface TableQrServicePort {
    QrTokenResponse createForTable(String tenantId, Integer tableNumber);
    QrTokenResponse getOrCreateForTable(String tenantId, Integer tableNumber);
    QrTokenResponse regenerateForTable(String tenantId, Integer tableNumber);
    void revokeForTable(String tenantId, Integer tableNumber);
    List<QrTokenResponse> listForTenant(String tenantId);
    List<QrTokenResponse> listHistoryForTenant(String tenantId, int limit);
    QrTokenService.QrContext validatePublicQrToken(String token);
}
