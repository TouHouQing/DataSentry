package com.touhouqing.datasentry.cleaning.security;

import com.touhouqing.datasentry.cleaning.enums.CleaningPermissionCode;
import com.touhouqing.datasentry.exception.ForbiddenException;
import com.touhouqing.datasentry.properties.DataSentryProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CleaningPermissionGuard {

	private static final String DEFAULT_SUPER_TOKEN = "*";

	private final DataSentryProperties dataSentryProperties;

	public void require(CleaningPermissionCode permissionCode) {
		if (permissionCode == null || !dataSentryProperties.getCleaning().getPermission().isEnabled()) {
			return;
		}
		HttpServletRequest request = currentRequest();
		if (request == null) {
			throw new ForbiddenException("缺少请求上下文，无法校验权限：" + permissionCode.code());
		}
		String headerName = resolveHeaderName();
		String rawPermissionText = request.getHeader(headerName);
		Set<String> permissionSet = parsePermissions(rawPermissionText);
		String requiredCode = permissionCode.code();
		if (hasSuperToken(permissionSet) || permissionSet.contains(requiredCode)) {
			return;
		}
		throw new ForbiddenException("缺少权限：" + requiredCode);
	}

	private HttpServletRequest currentRequest() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes)) {
			return null;
		}
		return servletAttributes.getRequest();
	}

	private String resolveHeaderName() {
		String configured = dataSentryProperties.getCleaning().getPermission().getHeaderName();
		if (configured == null || configured.isBlank()) {
			return "X-Cleaning-Permissions";
		}
		return configured;
	}

	private Set<String> parsePermissions(String rawPermissionText) {
		if (rawPermissionText == null || rawPermissionText.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(rawPermissionText.split("[,\\s]+"))
			.map(String::trim)
			.filter(token -> !token.isBlank())
			.collect(Collectors.toSet());
	}

	private boolean hasSuperToken(Set<String> permissionSet) {
		if (!dataSentryProperties.getCleaning().getPermission().isAllowSuperToken()) {
			return false;
		}
		String superToken = dataSentryProperties.getCleaning().getPermission().getSuperToken();
		String effectiveToken = (superToken == null || superToken.isBlank()) ? DEFAULT_SUPER_TOKEN : superToken;
		return permissionSet.contains(effectiveToken);
	}

}
