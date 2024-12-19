package com.mgmresorts.order.service.comparator;

import java.util.Comparator;

import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;

public class LineItemComparator implements Comparator<CartLineItem> {

    @Override
    public int compare(CartLineItem cli1, CartLineItem cli2) {
        if (cli1.getType() == ItemType.ROOM) {
            return -1;
        } else if (cli1.getType() == ItemType.SHOW) {
            return 1;
        } else {
            return 0;
        }
    }
}
