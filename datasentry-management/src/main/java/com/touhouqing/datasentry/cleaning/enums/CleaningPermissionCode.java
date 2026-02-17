package com.touhouqing.datasentry.cleaning.enums;

public enum CleaningPermissionCode {

	POLICY_PUBLISH("policy:publish"),

	WRITEBACK_EXECUTE("writeback:execute"),

	DELETE_HARD("delete:hard"),

	ROLLBACK_EXECUTE("rollback:execute"),

	AUDIT_EXPORT("audit:export");

	private final String code;

	CleaningPermissionCode(String code) {
		this.code = code;
	}

	public String code() {
		return this.code;
	}

}
