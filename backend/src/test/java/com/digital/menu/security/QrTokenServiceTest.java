package com.digital.menu.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QrTokenServiceTest {

    private static final String VALID_BASE64_SECRET = "ZGV2LWxvY2FsLXFyLXNlY3JldC0xMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMw==";

    @Test
    void generateAndParseToken_roundTrip() {
        QrTokenService service = new QrTokenService();
        ReflectionTestUtils.setField(service, "qrSecret", VALID_BASE64_SECRET);
        service.validateSecret();

        String token = service.generateToken("tenant-a", 5);
        QrTokenService.QrContext context = service.parseToken(token);

        assertEquals("tenant-a", context.tenantId());
        assertEquals(5, context.tableNumber());
    }

    @Test
    void validateSecret_rejectsInvalidBase64() {
        QrTokenService service = new QrTokenService();
        ReflectionTestUtils.setField(service, "qrSecret", "not-base64");
        assertThrows(IllegalStateException.class, service::validateSecret);
    }

    @Test
    void generateToken_requiresValidInputs() {
        QrTokenService service = new QrTokenService();
        ReflectionTestUtils.setField(service, "qrSecret", VALID_BASE64_SECRET);
        service.validateSecret();

        assertThrows(IllegalArgumentException.class, () -> service.generateToken("", 1));
        assertThrows(IllegalArgumentException.class, () -> service.generateToken("tenant-a", 0));
    }
}

