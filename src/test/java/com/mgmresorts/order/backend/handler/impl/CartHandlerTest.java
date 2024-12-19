package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.dto.OutHeader;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.order.backend.access.ICartAccess;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.Order.Status;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.Type;
import com.mgmresorts.order.dto.services.Version;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.CartType;
import com.mgmresorts.shopping.cart.dto.CartVersion;
import com.mgmresorts.shopping.cart.dto.GuestProfile;
import com.mgmresorts.shopping.cart.dto.ItemType;
import com.mgmresorts.shopping.cart.dto.PriceDetails;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;
import com.mgmresorts.shopping.cart.dto.services.HandleCheckoutRequest;
import com.mgmresorts.shopping.cart.dto.services.ManageCartPaymentSessionRequest;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class CartHandlerTest {
    @Tested
    private CartHandler cartHandler;

    @Injectable
    private ICartAccess cartAccess;

    @Injectable
    private OAuthTokenRegistry registry;
    @Injectable
    private Executors executors;

    private final PodamFactoryImpl podamFactoryImpl = new PodamFactoryImpl();

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(cartAccess);
        ErrorManager.clean();
        new Errors();
    }

    public Cart getCart() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.ROOM);
        product.setLineItemPrice(100.00);
        product.setLineItemDeposit(50.00);
        product.setContent(
                "{\"propertyId\":\"e2704b04-d515-45b0-8afd-4fa1424ff0a8\",\"roomTypeId\":\"9401af33-8386-4958-9b8e-3d890b732b2a\",\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"customerId\":\"0\",\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2021-01-13\",\"checkOutDate\":\"2021-01-14\",\"numAdults\":1,\"numChildren\":0,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-01-13\",\"basePrice\":100014.99,\"customerPrice\":100014.99,\"price\":100014.99,\"programIdIsRateTable\":false,\"overridePrice\":-1,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"pricingRuleId\":\"fa1d4b3c-50bd-4fbf-8c9b-695c2c49f583\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-01-13\",\"amount\":100059.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":45,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2021-01-13\",\"amount\":13388.03,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":6.02,\"item\":\"Resort Fee Tax\"}]}]},\"amountDue\":0,\"ratesSummary\":{\"roomSubtotal\":100014.9900,\"programDiscount\":0.0000,\"discountedSubtotal\":100014.9900,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":100014.9900,\"resortFeeAndTax\":51.0200,\"roomChargeTax\":13382.0100,\"reservationTotal\":113448.0200,\"depositDue\":113397.0000,\"balanceUponCheckIn\":51.0200},\"depositDetails\":{\"dueDate\":\"2021-01-11\",\"amountPolicy\":\"Nights\",\"amount\":113397,\"forfeitDate\":\"2021-01-10\",\"forfeitAmount\":113397,\"overrideAmount\":-1,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"72H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2021-01-13\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIWB\"}]}");
        lineItems.add(product);

        Cart cart = new Cart();
        cart.setCartId("id");
        cart.setType(CartType.GLOBAL);
        cart.setVersion(CartVersion.V1);
        // cart.setTotalBalanceDue(0.00);
        cart.setCartLineItems(lineItems);
        cart.setPriceDetails(new PriceDetails());
        cart.getPriceDetails().setTotalDeposit(200.00);
        OutHeader header = new OutHeader();
        header.setApiVersion("apiversion");
        header.setExecutionId("executionId");

        CartResponse cartResp = new CartResponse();
        cartResp.setCart(cart);
        cartResp.setHeader(header);
        // Cart Response Mock Object End
        return cart;
    }

    @Test
    void validateCartResponsePostiveTest() throws AppException {

        Cart cart = getCart();
        String cartId = "cartId";
        String mgmId = "mgmId";
        cart.getCartLineItems().get(0).setStatus(CartLineItem.Status.SAVED);

        assertDoesNotThrow(() -> cartHandler.validateCartResponse(cart, cartId, mgmId));
    }

    @Test
    void validateCartResponseNoCartTest() throws AppException {
        String cartId = "cartId";
        String mgmId = "mgmId";

        AppException exception = assertThrows(AppException.class, () -> {
            cartHandler.validateCartResponse(null, cartId, mgmId);
        });
        assertTrue(exception.getDescription().contains("No cart was found with cartId: " + cartId + " and mgmId: " + mgmId));
        assertEquals(exception.getCode(), Errors.NO_CART_FOUND);
    }

    @Test
    void validateCartResponseEmptyCartTest() throws AppException {

        Cart cart = getCart();
        cart.setCartLineItems(new ArrayList<CartLineItem>());
        String cartId = "cartId";
        String mgmId = "mgmId";

        AppException exception = assertThrows(AppException.class, () -> {
            cartHandler.validateCartResponse(cart, cartId, mgmId);
        });
        assertTrue(exception.getDescription().contains("The cart with cartId: " + cartId + " and mgmId " + mgmId + " is empty. " + "There are no products to checkout."));
        assertEquals(exception.getCode(), Errors.EMPTY_CART);
    }

    @Test
    void validateCartResponseInvalidItemTest() throws AppException {
        final Order order = podamFactoryImpl.manufacturePojoWithFullData(Order.class);
        final OrderLineItem item1 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        item1.setStatus(OrderLineItem.Status.SUCCESS);
        order.getOrderLineItems().clear();
        order.getOrderLineItems().add(item1);
        final Cart cart = podamFactoryImpl.manufacturePojoWithFullData(Cart.class);
        final CartResponse cartResp = podamFactoryImpl.manufacturePojoWithFullData(CartResponse.class);
        final CartLineItem lineItem = podamFactoryImpl.manufacturePojoWithFullData(CartLineItem.class);
        lineItem.setStatus(CartLineItem.Status.INVALID);
        lineItem.setCartLineItemId(item1.getCartLineItemId());
        cart.setCartId("NewCartId");
        cart.getCartLineItems().add(lineItem);
        cartResp.setCart(cart);
        String cartId = "cartId";
        String mgmId = "mgmId";

        AppException exception = assertThrows(AppException.class, () -> {
            cartHandler.validateCartResponse(cart, cartId, mgmId);
        });

        assertEquals(exception.getCode(), Errors.NO_CHECKOUT_ELIGIBLE_INELIGIBLE_ITEMS);
    }

    @Test
    void moveCartTest() throws Exception {
        final Order order = podamFactoryImpl.manufacturePojoWithFullData(Order.class);
        final OrderLineItem item1 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        final OrderLineItem item2 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        item1.setStatus(OrderLineItem.Status.SUCCESS);
        item2.setStatus(null);
        order.getOrderLineItems().clear();
        order.getOrderLineItems().add(item1);
        order.getOrderLineItems().add(item2);
        final Cart cart = podamFactoryImpl.manufacturePojoWithFullData(Cart.class);
        final CartResponse cartResp = podamFactoryImpl.manufacturePojoWithFullData(CartResponse.class);
        final CartLineItem lineItem = podamFactoryImpl.manufacturePojoWithFullData(CartLineItem.class);
        lineItem.setCartLineItemId(item1.getCartLineItemId());
        cart.setCartId("NewCartId");
        cart.getCartLineItems().add(lineItem);
        cartResp.setCart(cart);
        List<String> failedProduct = new ArrayList<String>();
        failedProduct.add(item1.getCartLineItemId());
        new Expectations() {
            {
                cartAccess.handleCheckOut((HandleCheckoutRequest)any);
                result = cartResp;
            }
        };
        final String newCart = cartHandler.handleCheckout(order.getCartId(), failedProduct, true);
        assertEquals(cartResp.getCart().getCartId(), newCart);
    }
    
    @Test
    void moveCartTestNoCartResp() throws Exception {
        final Order order = podamFactoryImpl.manufacturePojoWithFullData(Order.class);
        final OrderLineItem item1 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        final OrderLineItem item2 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        item1.setStatus(OrderLineItem.Status.SUCCESS);
        item2.setStatus(null);
        order.getOrderLineItems().clear();
        order.getOrderLineItems().add(item1);
        order.getOrderLineItems().add(item2);
        List<String> failedProduct = new ArrayList<String>();
        failedProduct.add(item1.getCartLineItemId());
        new Expectations() {
            {
                cartAccess.handleCheckOut((HandleCheckoutRequest)any);
                result = null;
            }
        };
        
        AppException exception = assertThrows(AppException.class, () -> {
            cartHandler.handleCheckout(order.getCartId(), failedProduct, true);
        });
        assertEquals(exception.getCode(), Errors.UNABLE_TO_MOVE_PRODUCT);
        
    }
    
    @Test
    void moveCartTestNoCart() throws Exception {
        final Order order = podamFactoryImpl.manufacturePojoWithFullData(Order.class);
        final OrderLineItem item1 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        final OrderLineItem item2 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        item1.setStatus(OrderLineItem.Status.SUCCESS);
        item2.setStatus(null);
        order.getOrderLineItems().clear();
        order.getOrderLineItems().add(item1);
        order.getOrderLineItems().add(item2);
        final CartResponse cartResp = podamFactoryImpl.manufacturePojoWithFullData(CartResponse.class);
        cartResp.setCart(null);
        List<String> failedProduct = new ArrayList<String>();
        failedProduct.add(item1.getCartLineItemId());
        new Expectations() {
            {
                cartAccess.handleCheckOut((HandleCheckoutRequest)any);
                result = cartResp;
            }
        };

        final String response = cartHandler.handleCheckout(order.getCartId(), failedProduct, true);
        assertEquals(null, response);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void getCartTest() throws Exception {
        Cart cart =  getCart();
        cart.setCartId("cartId1");
        cart.setMgmId("mgmId1");
        cart.setMlife("mlifeId1");
        CartResponse cartResponse = new CartResponse();
        cartResponse.setCart(cart);
        new Expectations() {
            {
                cartAccess.getCart(anyString, (List<String[]>) any);
                result = cartResponse;
            }
        };
        Cart response = cartHandler.getCart("cartId1", "mgmId1", Type.GLOBAL, Version.V1);
        assertNotNull(response);
        assertEquals("cartId1" , response.getCartId());
        assertEquals("mgmId1" , response.getMgmId());
        assertEquals("mlifeId1" , response.getMlife());
        assertEquals(CartType.GLOBAL , response.getType());
        assertEquals(CartVersion.V1 , response.getVersion());
    }
    
    @Test
    void manageCartPaymentSessionTest() throws Exception {
        final Order order = podamFactoryImpl.manufacturePojoWithFullData(Order.class);
        final OrderLineItem item1 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        final OrderLineItem item2 = podamFactoryImpl.manufacturePojoWithFullData(OrderLineItem.class);
        item1.setStatus(OrderLineItem.Status.FAILURE);
        item2.setStatus(OrderLineItem.Status.FAILURE);
        order.getOrderLineItems().clear();
        order.getOrderLineItems().add(item1);
        order.getOrderLineItems().add(item2);
        order.setStatus(Status.FAILURE);
        final CartResponse cartResp = podamFactoryImpl.manufacturePojoWithFullData(CartResponse.class);
        cartResp.getCart().setCartId("cartId1");
        cartResp.getCart().setType(CartType.GLOBAL);
        cartResp.getCart().setVersion(CartVersion.V1);
        cartResp.getCart().setPaymentSessionId("paymentSessionId1");
        new Expectations() {
            {
                cartAccess.manageCartPaymentSession(anyString, (CartType) any, (CartVersion) any, (ManageCartPaymentSessionRequest) any);
                result = cartResp;
            }
        };
        
        GuestProfile guestProfile = podamFactoryImpl.manufacturePojoWithFullData(GuestProfile.class);

        final String response = cartHandler.manageCartPaymentSession("cartId1", CartType.GLOBAL, CartVersion.V1, guestProfile);
        new Verifications() {
            {
                cartAccess.manageCartPaymentSession(anyString, (CartType) any, (CartVersion) any, (ManageCartPaymentSessionRequest) any);
                times = 1;
            }
        };
        assertEquals("paymentSessionId1", response);
    }
}
