package com.touhouqing.datasentry.cleaning;

import com.touhouqing.datasentry.cleaning.enums.CleaningPermissionCode;
import com.touhouqing.datasentry.cleaning.security.CleaningPermissionGuard;
import com.touhouqing.datasentry.exception.ForbiddenException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CleaningPermissionGuardTest {

	private DataSentryProperties properties;

	private CleaningPermissionGuard guard;

	@BeforeEach
	public void setUp() {
		properties = new DataSentryProperties();
		guard = new CleaningPermissionGuard(properties);
	}

	@AfterEach
	public void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	public void shouldAllowWhenPermissionControlDisabled() {
		properties.getCleaning().getPermission().setEnabled(false);
		assertDoesNotThrow(() -> guard.require(CleaningPermissionCode.WRITEBACK_EXECUTE));
	}

	@Test
	public void shouldAllowWhenPermissionIncludedInHeader() {
		properties.getCleaning().getPermission().setEnabled(true);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Cleaning-Permissions", "writeback:execute");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		assertDoesNotThrow(() -> guard.require(CleaningPermissionCode.WRITEBACK_EXECUTE));
	}

	@Test
	public void shouldAllowWithSuperToken() {
		properties.getCleaning().getPermission().setEnabled(true);
		properties.getCleaning().getPermission().setAllowSuperToken(true);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Cleaning-Permissions", "*");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		assertDoesNotThrow(() -> guard.require(CleaningPermissionCode.AUDIT_EXPORT));
	}

	@Test
	public void shouldThrowWhenMissingPermission() {
		properties.getCleaning().getPermission().setEnabled(true);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Cleaning-Permissions", "policy:publish");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		assertThrows(ForbiddenException.class, () -> guard.require(CleaningPermissionCode.ROLLBACK_EXECUTE));
	}

}
