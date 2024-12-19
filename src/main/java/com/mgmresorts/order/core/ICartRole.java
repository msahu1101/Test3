package com.mgmresorts.order.core;

import com.mgmresorts.common.security.scope.IRole;
import com.mgmresorts.common.security.scope.Role;

@SuppressWarnings("deprecation")
public interface ICartRole extends IRole {
    String CHECKOUT = "cart:checkout";
    String ADMIN = "cart:admin";
    String READ = "order:read";

    public static class CartRole implements ICartRole {
        public static final Role CHECKOUT = new Role(ICartRole.CHECKOUT, "Scope to checkout a cart");
        public static final Role READ = new Role(ICartRole.READ, "Scope to read an order");

    }

}
