package com.campus.helper.security;

import com.campus.helper.common.BusinessException;
import org.springframework.security.core.Authentication;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(401, "请先登录或登录已过期");
        }
        return user;
    }

    public static boolean isAdmin(AuthenticatedUser user) {
        return "ADMIN".equals(user.role());
    }
}
