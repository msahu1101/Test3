package com.mgmresorts.order.backend.handler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.utils.ThreadContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.dto.OutHeader;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.notification.Email;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.itineraries.dto.client.services.CreateItineraryRequest;
import com.mgmresorts.order.backend.access.IItineraryAccess;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.CartLineItem;
import com.mgmresorts.shopping.cart.dto.ItemType;
import com.mgmresorts.shopping.cart.dto.PriceDetails;
import com.mgmresorts.shopping.cart.dto.services.CartResponse;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ItineraryHandlerTest {
    @Tested
    private ItineraryHandler itineraryHandler;

    @Injectable
    private IItineraryAccess itineraryAccess;
    @Injectable
    private SmtpEmailer smtpEmailer;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
    }

    @BeforeEach
    public void before() {
        assertNotNull(itineraryAccess);
        ErrorManager.clean();
        new Errors();
    }

    @Test
    void createItinerarySuccessTest() throws AppException, HttpFailureException {
        final String itrSuccessResp = Utils.readAllBytes("data/itinerary_create_success_response.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = itrSuccessResp;
            }
        };
        String itrNum = itineraryHandler.create(guestProfile, cart);

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
        };
        assertNotNull(itrNum);
        assertEquals("9328393730", itrNum);
    }

    @Test
    void createItineraryStatus400FailureTest() throws AppException, HttpFailureException {
        final String payload = Utils.readAllBytes("data/itinerary_create_invalid_request.json");
        final String message = "Missing mandatory information in the request: Customer Id";

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(400, payload, message, new String[] { "header" });
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(exception.getCode(), Errors.UNABLE_TO_CREATE_ITINERARY);
        assertEquals(exception.getHttpStatus().value(), 400);
    }

    @Test
    void createItineraryStatus500FailureTest() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary_create_invalid_request.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(500, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(exception.getCode(), Errors.UNABLE_TO_CREATE_ITINERARY);

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
        };
    }

    @Test
    void createItineraryStatusCustomerNotFoundFailureTest() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary_create_customer_not_found.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final HashMap claims = new HashMap();

        claims.put(Jwts.Claim.MGM_ID, "mgmID");
        claims.put(Jwts.Claim.MLIFE_NUMBER, "mlifeNumber");
        claims.put(Jwts.Claim.GSE_ID, "gseID");

        Jwts.Jwt jwt = new Jwts.Jwt("token", "alg", new Date(), claims, null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(404, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID, exception.getCode());

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
    }

    @Test
    void createItineraryStatusGseInternalServerErrorFailureTest() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary-create-gse-internal-server-error.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final HashMap claims = new HashMap();

        claims.put(Jwts.Claim.MGM_ID, "mgmID");
        claims.put(Jwts.Claim.MLIFE_NUMBER, "mlifeNumber");
        claims.put(Jwts.Claim.GSE_ID, "gseID");

        Jwts.Jwt jwt = new Jwts.Jwt("token", "alg", new Date(), claims, null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(500, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID, exception.getCode());

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
    }

    @Test
    void createItineraryStatusGseNotFoundFailureTest() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary-create-gse-customer-not-found.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final HashMap claims = new HashMap();

        claims.put(Jwts.Claim.MGM_ID, "mgmID");
        claims.put(Jwts.Claim.MLIFE_NUMBER, "mlifeNumber");
        claims.put(Jwts.Claim.GSE_ID, "gseID");

        Jwts.Jwt jwt = new Jwts.Jwt("token", "alg", new Date(), claims, null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(404, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID, exception.getCode());

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
    }

    @Test
    void createItineraryStatusGseBadRequestFailureTest() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary-create-gse-bad-request.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final HashMap claims = new HashMap();

        claims.put(Jwts.Claim.MGM_ID, "mgmID");
        claims.put(Jwts.Claim.MLIFE_NUMBER, "mlifeNumber");
        claims.put(Jwts.Claim.GSE_ID, "gseID");

        Jwts.Jwt jwt = new Jwts.Jwt("token", "alg", new Date(), claims, null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(400, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID, exception.getCode());

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
            {
                smtpEmailer.send((Email)any);
                times = 1;
            }
        };
    }

    @Test
    void createItineraryStatusGseBadRequestFailureTestEmailNotSent() throws AppException, HttpFailureException {
        final String itrFailureResp = Utils.readAllBytes("data/itinerary-create-gse-bad-request.json");

        final GuestProfile guestProfile = new GuestProfile();
        guestProfile.setFirstName("Fname");
        guestProfile.setLastName("Lname");

        final HashMap claims = new HashMap();
        Jwts.Jwt jwt = new Jwts.Jwt("token", "alg", new Date(), claims, null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);

        final Cart cart = getCart();

        new Expectations() {
            {
                itineraryAccess.createItinerary((CreateItineraryRequest) any);
                result = new HttpFailureException(400, itrFailureResp, "Unable to create itinerary", new String[] { "header" });
            }
        };
        AppException exception = assertThrows(AppException.class, () -> {
            itineraryHandler.create(guestProfile, cart);
        });
        assertEquals(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID, exception.getCode());

        new Verifications() {
            {
                CreateItineraryRequest c;
                itineraryAccess.createItinerary(c = (CreateItineraryRequest) withCapture());
                assertNotNull(c.getItinerary().getTripParams());
                assertEquals(7, c.getItinerary().getTripParams().getNumAdults());
                assertEquals(8, c.getItinerary().getTripParams().getNumChildren());
                assertEquals(LocalDate.of(2021, 1, 21), c.getItinerary().getTripParams().getDepartureDate());
                assertEquals(LocalDate.of(2021, 1, 10), c.getItinerary().getTripParams().getArrivalDate());
            }
            {
                smtpEmailer.send((Email)any);
                times = 0;
            }
        };
    }

    public Cart getCart() {
        // Cart Response Mock Object Start
        List<CartLineItem> lineItems = new ArrayList<>();
        CartLineItem product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.ROOM);
        product.setLineItemDeposit(100.00);
        product.setLineItemPrice(50.00);
        product.setContent(
                "{\"propertyId\":\"e2704b04-d515-45b0-8afd-4fa1424ff0a8\",\"roomTypeId\":\"9401af33-8386-4958-9b8e-3d890b732b2a\",\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"customerId\":\"0\",\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2021-01-10\",\"checkOutDate\":\"2021-01-14\",\"numAdults\":1,\"numChildren\":8,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-01-13\",\"basePrice\":100014.99,\"customerPrice\":100014.99,\"price\":100014.99,\"programIdIsRateTable\":false,\"overridePrice\":-1,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"pricingRuleId\":\"fa1d4b3c-50bd-4fbf-8c9b-695c2c49f583\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-01-13\",\"amount\":100059.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":45,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2021-01-13\",\"amount\":13388.03,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":6.02,\"item\":\"Resort Fee Tax\"}]}]},\"amountDue\":0,\"ratesSummary\":{\"roomSubtotal\":100014.9900,\"programDiscount\":0.0000,\"discountedSubtotal\":100014.9900,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":100014.9900,\"resortFeeAndTax\":51.0200,\"roomChargeTax\":13382.0100,\"reservationTotal\":113448.0200,\"depositDue\":113397.0000,\"balanceUponCheckIn\":51.0200},\"depositDetails\":{\"dueDate\":\"2021-01-11\",\"amountPolicy\":\"Nights\",\"amount\":113397,\"forfeitDate\":\"2021-01-10\",\"forfeitAmount\":113397,\"overrideAmount\":-1,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"72H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2021-01-13\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIWB\"}]}");
        lineItems.add(product);

        product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.ROOM);
        product.setLineItemDeposit(100.00);
        product.setLineItemPrice(50.00);
        product.setContent(
                "{\"propertyId\":\"e2704b04-d515-45b0-8afd-4fa1424ff0a8\",\"roomTypeId\":\"9401af33-8386-4958-9b8e-3d890b732b2a\",\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"customerId\":\"0\",\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2021-01-13\",\"checkOutDate\":\"2021-01-21\",\"numAdults\":7,\"numChildren\":2,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-01-13\",\"basePrice\":100014.99,\"customerPrice\":100014.99,\"price\":100014.99,\"programIdIsRateTable\":false,\"overridePrice\":-1,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"pricingRuleId\":\"fa1d4b3c-50bd-4fbf-8c9b-695c2c49f583\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-01-13\",\"amount\":100059.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":45,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2021-01-13\",\"amount\":13388.03,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":6.02,\"item\":\"Resort Fee Tax\"}]}]},\"amountDue\":0,\"ratesSummary\":{\"roomSubtotal\":100014.9900,\"programDiscount\":0.0000,\"discountedSubtotal\":100014.9900,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":100014.9900,\"resortFeeAndTax\":51.0200,\"roomChargeTax\":13382.0100,\"reservationTotal\":113448.0200,\"depositDue\":113397.0000,\"balanceUponCheckIn\":51.0200},\"depositDetails\":{\"dueDate\":\"2021-01-11\",\"amountPolicy\":\"Nights\",\"amount\":113397,\"forfeitDate\":\"2021-01-10\",\"forfeitAmount\":113397,\"overrideAmount\":-1,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"72H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2021-01-13\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIWB\"}]}");
        lineItems.add(product);

        product = new CartLineItem();
        product.setProductId("id");
        product.setType(ItemType.ROOM);
        product.setLineItemPrice(100.00);
        product.setLineItemDeposit(50.00);
        product.setContent(
                "{\"propertyId\":\"e2704b04-d515-45b0-8afd-4fa1424ff0a8\",\"roomTypeId\":\"9401af33-8386-4958-9b8e-3d890b732b2a\",\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"customerId\":\"0\",\"guaranteeCode\":\"CC\",\"tripDetails\":{\"checkInDate\":\"2021-01-14\",\"checkOutDate\":\"2021-01-19\",\"numAdults\":4,\"numChildren\":2,\"numRooms\":1},\"bookings\":[{\"date\":\"2021-01-13\",\"basePrice\":100014.99,\"customerPrice\":100014.99,\"price\":100014.99,\"programIdIsRateTable\":false,\"overridePrice\":-1,\"overrideProgramIdIsRateTable\":false,\"isComp\":false,\"resortFeeIsSpecified\":false,\"resortFee\":0,\"programId\":\"279188f0-2e78-4f54-a4c2-703a2c52d0e6\",\"pricingRuleId\":\"fa1d4b3c-50bd-4fbf-8c9b-695c2c49f583\"}],\"chargesAndTaxes\":{\"charges\":[{\"date\":\"2021-01-13\",\"amount\":100059.99,\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"ExtraGuestCharge\",\"amount\":0,\"item\":\"Extra Guest Charge\"},{\"itemType\":\"ResortFee\",\"amount\":45,\"item\":\"Resort Fee\"}]}],\"taxesAndFees\":[{\"date\":\"2021-01-13\",\"amount\":13388.03,\"itemized\":[{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"},{\"itemType\":\"ExtraGuestChargeTax\",\"amount\":0,\"item\":\"Extra Guest Tax\"},{\"itemType\":\"ResortFeeTax\",\"amount\":6.02,\"item\":\"Resort Fee Tax\"}]}]},\"amountDue\":0,\"ratesSummary\":{\"roomSubtotal\":100014.9900,\"programDiscount\":0.0000,\"discountedSubtotal\":100014.9900,\"roomRequestsTotal\":0.0000,\"adjustedRoomSubtotal\":100014.9900,\"resortFeeAndTax\":51.0200,\"roomChargeTax\":13382.0100,\"reservationTotal\":113448.0200,\"depositDue\":113397.0000,\"balanceUponCheckIn\":51.0200},\"depositDetails\":{\"dueDate\":\"2021-01-11\",\"amountPolicy\":\"Nights\",\"amount\":113397,\"forfeitDate\":\"2021-01-10\",\"forfeitAmount\":113397,\"overrideAmount\":-1,\"depositRuleCode\":\"1NT\",\"cancellationRuleCode\":\"72H\",\"itemized\":[{\"itemType\":\"RoomCharge\",\"amount\":100014.99,\"item\":\"Room Charge\"},{\"itemType\":\"RoomChargeTax\",\"amount\":13382.01,\"item\":\"Room Tax\"}]},\"depositPolicy\":{\"depositRequired\":true,\"creditCardRequired\":true},\"markets\":[{\"date\":\"2021-01-13\",\"marketCode\":\"TFIT\",\"sourceCode\":\"TFITIWB\"}]}");
        lineItems.add(product);

        Cart cart = new Cart();
        cart.setCartId("id");
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
}
