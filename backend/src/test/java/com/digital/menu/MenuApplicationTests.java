package com.digital.menu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"app.jwt.secret=ZGV2LXRlc3Qtand0LXNlY3JldC0xMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ=",
	"app.qr.secret=ZGV2LXRlc3QtcXItc2VjcmV0LTEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNA==",
	"app.seed.soy-affair.enabled=false"
})
class MenuApplicationTests {

	@Test
	void contextLoads() {
	}

}
