package com.littleisles.domain.reg;

public final class PermissionPolicy {
    private PermissionPolicy() {}

    public static boolean hasDashboardAccess(Role role) {
        return role != null;
    }
}
