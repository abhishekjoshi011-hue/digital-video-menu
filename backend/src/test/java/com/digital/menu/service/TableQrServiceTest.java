package com.digital.menu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digital.menu.dto.QrTokenResponse;
import com.digital.menu.model.TableQrCode;
import com.digital.menu.repository.TableQrCodeRepository;
import com.digital.menu.security.QrTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TableQrServiceTest {

    @Mock
    private TableQrCodeRepository tableQrCodeRepository;

    @Mock
    private QrTokenService qrTokenService;

    private TableQrService service;

    @BeforeEach
    void setUp() {
        service = new TableQrService(tableQrCodeRepository, qrTokenService);
        ReflectionTestUtils.setField(service, "publicMenuBaseUrl", "http://localhost:3000");
    }

    @Test
    void createForTable_throwsWhenActiveQrExists() {
        TableQrCode existing = activeQr("tenant-a", 7, "tok-old");
        when(tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue("tenant-a", 7))
            .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.createForTable("tenant-a", 7));
    }

    @Test
    void getOrCreateForTable_returnsExistingActiveQr() {
        TableQrCode existing = activeQr("tenant-a", 3, "tok-123");
        when(tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue("tenant-a", 3))
            .thenReturn(Optional.of(existing));

        QrTokenResponse response = service.getOrCreateForTable("tenant-a", 3);

        assertEquals("tok-123", response.getToken());
        assertEquals("http://localhost:3000/?t=tok-123", response.getMenuUrl());
    }

    @Test
    void regenerateForTable_deactivatesOldAndCreatesNew() {
        TableQrCode old = activeQr("tenant-a", 4, "tok-old");
        when(tableQrCodeRepository.findByTenantIdAndTableNumberAndActiveTrue("tenant-a", 4))
            .thenReturn(Optional.of(old))
            .thenReturn(Optional.empty());
        when(qrTokenService.generateToken("tenant-a", 4)).thenReturn("tok-new");
        when(tableQrCodeRepository.save(any(TableQrCode.class))).thenAnswer(inv -> inv.getArgument(0));

        QrTokenResponse response = service.regenerateForTable("tenant-a", 4);

        assertEquals("tok-new", response.getToken());
        verify(tableQrCodeRepository).save(old);
    }

    @Test
    void listHistoryForTenant_respectsLimit() {
        TableQrCode a = activeQr("tenant-a", 1, "a");
        TableQrCode b = activeQr("tenant-a", 2, "b");
        TableQrCode c = activeQr("tenant-a", 3, "c");
        when(tableQrCodeRepository.findByTenantIdOrderByUpdatedAtDesc("tenant-a"))
            .thenReturn(List.of(a, b, c));

        List<QrTokenResponse> history = service.listHistoryForTenant("tenant-a", 2);

        assertEquals(2, history.size());
        assertEquals("a", history.get(0).getToken());
        assertEquals("b", history.get(1).getToken());
    }

    private TableQrCode activeQr(String tenantId, int table, String token) {
        TableQrCode qr = new TableQrCode();
        qr.setTenantId(tenantId);
        qr.setTableNumber(table);
        qr.setToken(token);
        qr.setMenuUrl("http://localhost:3000/?t=" + token);
        qr.setActive(true);
        qr.setCreatedAt(Instant.now());
        qr.setUpdatedAt(Instant.now());
        return qr;
    }
}

