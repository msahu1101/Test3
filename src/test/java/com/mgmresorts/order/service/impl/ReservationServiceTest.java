package com.mgmresorts.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mgmresorts.common.concurrent.Result;
import com.mgmresorts.order.entity.OrderStatus;
import com.mgmresorts.order.entity.ProductType;
import com.mgmresorts.order.entity.Type;
import com.mgmresorts.order.entity.Version;
import com.mgmresorts.order.service.task.ReservationTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mgmresorts.common.concurrent.Executor;
import com.mgmresorts.common.concurrent.Executors;
import com.mgmresorts.common.errors.ErrorManager;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.exception.SourceAppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.http.IHttpService;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.ThreadContext;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.dbs.model.SearchReservationResponse;
import com.mgmresorts.dbs.model.SearchReservationResponseRestaurantReservationList;
import com.mgmresorts.order.PaymentAuthFields;
import com.mgmresorts.order.PaymentSessionBaseFields;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.handler.ICartHandler;
import com.mgmresorts.order.backend.handler.IItineraryHandler;
import com.mgmresorts.order.backend.handler.IPaymentHandler;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.backend.handler.IProfileHandler;
import com.mgmresorts.order.backend.handler.impl.CartHandler;
import com.mgmresorts.order.backend.handler.impl.ItineraryHandler;
import com.mgmresorts.order.backend.handler.impl.ProfileHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.database.access.IOrderProgressAccess;
import com.mgmresorts.order.dto.Billing;
import com.mgmresorts.order.dto.Payment;
import com.mgmresorts.order.dto.services.CancelReservationRequest;
import com.mgmresorts.order.dto.services.CancelReservationResponse;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.order.dto.services.Order;
import com.mgmresorts.order.dto.services.Order.Status;
import com.mgmresorts.order.dto.services.OrderLineItem;
import com.mgmresorts.order.dto.services.PreviewReservationRequest;
import com.mgmresorts.order.dto.services.PreviewReservationResponse;
import com.mgmresorts.order.dto.services.ReservationType;
import com.mgmresorts.order.dto.services.RetrieveReservationResponse;
import com.mgmresorts.order.dto.services.SourceSystemError;
import com.mgmresorts.order.dto.services.UpdateReservationRequest;
import com.mgmresorts.order.dto.services.UpdateReservationResponse;
import com.mgmresorts.order.entity.LineItem;
import com.mgmresorts.order.entity.OrderConfirmationMapping;
import com.mgmresorts.order.errors.ApplicationError;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.logging.OrderFinancialImpact;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.order.service.task.ReservationHandlerFactory;
import com.mgmresorts.order.service.task.handler.DiningHandler;
import com.mgmresorts.order.service.task.handler.RoomHandler;
import com.mgmresorts.order.service.transformer.OrderFinancialImpactTransformer;
import com.mgmresorts.order.service.transformer.OrderTransformer;
import com.mgmresorts.order.utils.Orders;
import com.mgmresorts.psm.model.SessionError;
import com.mgmresorts.rbs.model.CancelRoomReservationResponse;
import com.mgmresorts.rbs.model.CancelRoomReservationV3Request;
import com.mgmresorts.rbs.model.Deposit;
import com.mgmresorts.rbs.model.ErrorResponse;
import com.mgmresorts.rbs.model.ErrorResponseError;
import com.mgmresorts.rbs.model.GetRoomReservationResponse;
import com.mgmresorts.rbs.model.ModifyCommitErrorResponse;
import com.mgmresorts.rbs.model.ModifyCommitPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequest;
import com.mgmresorts.rbs.model.PremodifyPutRequestTripDetails;
import com.mgmresorts.rbs.model.ReservationProfile;
import com.mgmresorts.rbs.model.RoomReservationResponse;
import com.mgmresorts.sbs.model.ShowReservationResponse;
import com.mgmresorts.shopping.cart.dto.ItemType;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;

@SuppressWarnings("unchecked")
public class ReservationServiceTest {

    @Tested
    ReservationService reservationService;
    
    private final JSonMapper mapper = new JSonMapper();

    @Injectable
    private OAuthTokenRegistry registry;
    @Injectable
    private IHttpService service;
    @Injectable
    private RoomHandler roomHandler;
    @Injectable
    private IProductHandler showHandler;
    @Injectable
    private IOrderProgressAccess orderProgressAccess;

    @Injectable
    private ReservationHandlerFactory task;

    @Injectable
    private Executors executors;

    @Injectable
    private IContentAccess contentAccess;

    @Injectable
    private IPaymentHandler paymentHandler;

    @Injectable
    private Orders orders;

    @Injectable
    private IPaymentSessionCommonHandler paymentSessionCommonHandler;

    @Mocked
    private Executor executor;

    @BeforeAll
    public static void init() {
        System.setProperty("runtime.environment", "junit");
        Jwts.Jwt jwt = new Jwts.Jwt(token, "alg", new Date(), new HashMap<>(), null, null, null, null);
        ThreadContext.getContext().get().setJwt(jwt);
    }

    @BeforeEach
    public void before() {
        assertNotNull(registry);
        assertNotNull(service);
        assertNotNull(roomHandler);
        ErrorManager.clean();
        new Errors();
    }

    static final String token = "eyJraWQiOiJtZ0NzNEE5R3VpSmsxcVRQQmNkcW1BYlNfcGwyMmVvVU1PSnFzM3hQOVh3IiwiYWxnIjoiUlMyNTYifQ."
            + "eyJ2ZXIiOjEsImp0aSI6IkFULmMwNy16akU4X2JfWVVmQWxKT2dzWl9BaDJWdjdpdi1hVk9sYXhQaUh1X00iLCJpc3MiOiJodHRwczov"
            + "L2lhbS5tcmdyZXMuY29tL29hdXRoMi9hdXNwaDdlenAzR2trazhXTjBoNyIsImF1ZCI6Imh0dHBzOi8vYXBpLm1nbXJlc29ydHMuY29t"
            + "IiwiaWF0IjoxNjI1OTIyNTEyLCJleHAiOjE2MjU5MjYxMTIsImNpZCI6ImRldm9wc19jYXJ0cyIsInNjcCI6WyJyb29tcy5yZXNlcnZhd"
            + "Glvbi5jaGFyZ2VzOnJlYWQiLCJyb29tcy5yZXNlcnZhdGlvbjp1cGRhdGUiLCJjYXJ0OmNoZWNrb3V0Iiwicm9vbXMuYXZhaWxhYmlsa"
            + "XR5OnJlYWQiLCJyb29tcy5wcm9ncmFtOnJlYWQiLCJjYXJ0OnVwZGF0ZSIsImNhcnQ6ZGVsZXRlIiwiY2FydDpyZWFkIiwiY2FydDpjcm"
            + "VhdGUiXSwic3ViIjoiZGV2b3BzX2NhcnRzIiwiZ3JvdXBzIjpbIm1nbV9zZXJ2aWNlIl19.R9kpKGhzRnGa354KZsJQHXo6jo2nowPdMGN"
            + "bix0slFcld6U2ydusIa5zgtPKabpZEJEpZT0wJ4--USft2oQQv9_8Lk-Xw8p6pd11daNwEgOh3uvibSJLsFkMYEI7j1AS5D2oHZvZwqjAt"
            + "gL7_6geUDm9O_bNlDHG_8vJ9JCaKj_Ae9eDRsu2BR31f-p8IvZFGrRwAEZZqO4hVqcc8tbYeo3JoGoJbH_gyZb5Qed8jDP73rD52iZr61n"
            + "HpuAD-SCdNt33IZEhfR35wxf_wdVj34_wdEKQ3wf075poEPqQ6vzoP7axaBhdqZsm8ydkHH_YoFTr8AM_3V-cNIWrAfTNpw";

    @Test
    void getReservationTestMissingConfirmationNumber_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                          @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                          @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                          @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, HttpFailureException {
        AppException exception = assertThrows(AppException.class, () -> {
            reservationService.getReservation(null,"John","Doe", ReservationType.ROOM, true, null);
        });
        assertTrue(exception.getDescription().contains("confirmation number is mandatory"));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST_INFORMATION);
    }

    @Test
    void getReservationTestMissingFirstName_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                 @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                 @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                 @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, HttpFailureException {
        AppException exception = assertThrows(AppException.class, () -> {
            reservationService.getReservation("1234",null,"Doe",ReservationType.ROOM, true, null);
        });
        assertTrue(exception.getDescription().contains("first name is mandatory"));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST_INFORMATION);
    }

    @Test
    void getReservationTestMissingLastName_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, HttpFailureException {
        AppException exception = assertThrows(AppException.class, () -> {
            reservationService.getReservation("1234","John",null,ReservationType.ROOM, true, null);
        });
        assertTrue(exception.getDescription().contains("last name is mandatory"));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST_INFORMATION);
    }

    @Test
    void getReservationTestMissingType_Room(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                            @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                            @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                            @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, HttpFailureException {
        AppException exception = assertThrows(AppException.class, () -> {
            reservationService.getReservation("1234","John","Doe",null, true, null);
        });
        assertTrue(exception.getDescription().contains("reservation type is mandatory"));
        assertEquals(exception.getCode(), Errors.INVALID_REQUEST_INFORMATION);
    }

    @Test
    void getReservationRoomTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);
        retrieveReservationResponse.setPaymentSessionId("1234");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.getReservation(anyString, anyString, anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("1234", "John", "Doe", ReservationType.ROOM, true, null);
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }


    @Test
    void getReservationPackageV2Test(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                @Injectable OrderTransformer orderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory, @Injectable IOrderAccess orderAccess
    ) throws AppException, HttpFailureException {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("M083E7632");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        roomReservationResponse.setIsStayDateModifiable(true);
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        RetrieveReservationResponse roomRetrieveReservationResponse = new RetrieveReservationResponse();
        roomRetrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);

        ShowReservationResponse showReservationResponse = new ShowReservationResponse();
        showReservationResponse.setConfirmationNumber("5678");
        showReservationResponse.setProfile(new com.mgmresorts.sbs.model.ReservationProfile());
        showReservationResponse.getProfile().setFirstName("John");
        showReservationResponse.getProfile().setLastName("Doe");
        RetrieveReservationResponse showRetrieveReservationResponse = new RetrieveReservationResponse();
        showRetrieveReservationResponse.setShowReservationResponse(showReservationResponse);

        SearchReservationResponse searchReservationResponse = new SearchReservationResponse();
        SearchReservationResponseRestaurantReservationList searchReservationResponseRestaurantReservationList = new SearchReservationResponseRestaurantReservationList();
        searchReservationResponseRestaurantReservationList.setConfirmationNumber("3823");
        searchReservationResponseRestaurantReservationList.setFirstName("John");
        searchReservationResponseRestaurantReservationList.setLastName("Doe");
        List<SearchReservationResponseRestaurantReservationList> reservationLists = new ArrayList<>();
        reservationLists.add(searchReservationResponseRestaurantReservationList);
        searchReservationResponse.setRestaurantReservationList(reservationLists);
        RetrieveReservationResponse diningRetrieveReservationResponse = new RetrieveReservationResponse();
        diningRetrieveReservationResponse.setDiningReservationResponse(searchReservationResponse);

        List<Result<RetrieveReservationResponse>> reservationResponses = new ArrayList<>();
        Result<RetrieveReservationResponse> roomResult = new Result<>(roomRetrieveReservationResponse);
        reservationResponses.add(roomResult);
        Result<RetrieveReservationResponse> showResult = new Result<>(showRetrieveReservationResponse);
        reservationResponses.add(showResult);
        Result<RetrieveReservationResponse> diningResult = new Result<>(diningRetrieveReservationResponse);
        reservationResponses.add(diningResult);

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(Type.PACKAGE);
        orderEntity.setVersion(Version.V2);
        orderEntity.setLineItems(new ArrayList<>());

        Order orderDto = new Order();
        orderDto.setCartId(orderEntity.getCartId());
        orderDto.setId(orderEntity.getId());
        orderDto.setVersion(com.mgmresorts.order.dto.services.Version.V2);
        orderDto.setType(com.mgmresorts.order.dto.services.Type.PACKAGE);
        
        List<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will succeed
        OrderLineItem itemOne = new OrderLineItem();
        itemOne.setOrderLineItemId("OrderLineItemId1");
        itemOne.setCartLineItemId("CartLineItemId1");
        itemOne.setProductType(OrderLineItem.ProductType.SHOW);
        itemOne.setStatus(OrderLineItem.Status.SUCCESS);
        orderLineItems.add(itemOne);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("OrderLineItemId2");
        itemTwo.setCartLineItemId("CartLineItemId2");
        itemTwo.setProductType(OrderLineItem.ProductType.ROOM);
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        orderLineItems.add(itemTwo);
        // ----------------------------------------------
        
        orderDto.setOrderLineItems(orderLineItems);
        orderDto.setStatus(Status.SUCCESS);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = createPackageV2Order();
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                executor.invoke((List<ReservationTask>) any);
                result = reservationResponses;
            }
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = orderDto;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("M083E7632", "John", "Doe", ReservationType.ROOM, false, null);

        //Room
        assertEquals("M083E7632", retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName());
        assertFalse(retrieveBookingResponse.getRoomReservationResponse().isIsStayDateModifiable());

        //Show
        assertEquals("5678", retrieveBookingResponse.getShowReservationResponse().getConfirmationNumber());
        assertEquals("John", retrieveBookingResponse.getShowReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", retrieveBookingResponse.getShowReservationResponse().getProfile().getLastName());

        //Dining
        assertEquals("3823", retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getConfirmationNumber());
        assertEquals("John", retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getFirstName());
        assertEquals("Doe", retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getLastName());

        assertNotNull(retrieveBookingResponse.getOrder());
        assertEquals("V2", retrieveBookingResponse.getOrder().getVersion().value());
        assertEquals("PACKAGE", retrieveBookingResponse.getOrder().getType().value());
        
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getStatus().value());
        
        assertNull(retrieveBookingResponse.getPaymentSessionId());
        assertTrue(retrieveBookingResponse.getIsPackage());
    }

    @Test
    void getReservationPackageV2TestWithPaymentSession(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                     @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                     @Injectable OrderTransformer orderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                     @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                     @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                     @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory, @Injectable IOrderAccess orderAccess
    ) throws AppException, HttpFailureException {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("M083E7632");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        roomReservationResponse.setIsStayDateModifiable(true);
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        RetrieveReservationResponse roomRetrieveReservationResponse = new RetrieveReservationResponse();
        roomRetrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);

        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setRoomReservationResponse(getRoomReservationResponse.getRoomReservation());
        retrieveReservationResponse.setPaymentSessionId("sessionId");

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setStatus(OrderStatus.SUCCESS);
        orderEntity.setType(Type.PACKAGE);
        orderEntity.setVersion(Version.V2);
        orderEntity.setLineItems(new ArrayList<>());

        Order orderDto = new Order();
        orderDto.setCartId(orderEntity.getCartId());
        orderDto.setId(orderEntity.getId());
        orderDto.setVersion(com.mgmresorts.order.dto.services.Version.V2);
        orderDto.setType(com.mgmresorts.order.dto.services.Type.PACKAGE);

        List<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will succeed
        OrderLineItem itemOne = new OrderLineItem();
        itemOne.setOrderLineItemId("OrderLineItemId1");
        itemOne.setCartLineItemId("CartLineItemId1");
        itemOne.setProductType(OrderLineItem.ProductType.SHOW);
        itemOne.setStatus(OrderLineItem.Status.SUCCESS);
        orderLineItems.add(itemOne);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("OrderLineItemId2");
        itemTwo.setCartLineItemId("CartLineItemId2");
        itemTwo.setProductType(OrderLineItem.ProductType.ROOM);
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        orderLineItems.add(itemTwo);
        // ----------------------------------------------

        orderDto.setOrderLineItems(orderLineItems);
        orderDto.setStatus(Status.SUCCESS);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = createPackageV2Order();
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.getReservation(anyString, anyString, anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = orderDto;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("M083E7632", "John", "Doe", ReservationType.ROOM, true, null);

        //Room
        assertEquals("M083E7632", retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName());
        assertFalse(retrieveBookingResponse.getRoomReservationResponse().isIsStayDateModifiable());

        assertNull(retrieveBookingResponse.getShowReservationResponse());
        assertNull(retrieveBookingResponse.getDiningReservationResponse());

        assertNotNull(retrieveBookingResponse.getOrder());
        assertEquals("V2", retrieveBookingResponse.getOrder().getVersion().value());
        assertEquals("PACKAGE", retrieveBookingResponse.getOrder().getType().value());

        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getStatus().value());

        assertEquals("sessionId", retrieveBookingResponse.getPaymentSessionId());
        assertTrue(retrieveBookingResponse.getIsPackage());
    }

    private com.mgmresorts.order.entity.Order createPackageV2Order() {
        final com.mgmresorts.order.entity.Order order = new com.mgmresorts.order.entity.Order();
        order.setId("123456");
        order.setLineItems(getOrderLineItems());
        return order;
    }

    private List<LineItem> getOrderLineItems() {
        final List<LineItem> lineItems = new ArrayList<>();
        final LineItem lineItemRoom = new LineItem();
        lineItemRoom.setConfirmationNumber("M083E7632");
        lineItemRoom.setProductType(ProductType.ROOM);
        lineItems.add(lineItemRoom);
        final LineItem lineItemShow = new LineItem();
        lineItemShow.setConfirmationNumber("5678");
        lineItemShow.setProductType(ProductType.SHOW);
        lineItems.add(lineItemShow);
        final LineItem lineItemDining = new LineItem();
        lineItemDining.setConfirmationNumber("3823");
        lineItemDining.setProductType(ProductType.DINING);
        lineItems.add(lineItemDining);
        return lineItems;
    }


    @Test
    void getReservationPackageV2PartialFailureTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                     @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                     @Injectable OrderTransformer orderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                     @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                     @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                     @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory, @Injectable IOrderAccess orderAccess
    ) throws AppException, HttpFailureException {

        RetrieveReservationResponse showRetrieveReservationResponse = new RetrieveReservationResponse();
        com.mgmresorts.sbs.model.ErrorResponse errorResponse = new com.mgmresorts.sbs.model.ErrorResponse();
        com.mgmresorts.sbs.model.ErrorResponseError errorResponseError = new com.mgmresorts.sbs.model.ErrorResponseError();
        errorResponseError.setCode( "620-2-214");
        errorResponseError.setMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        errorResponse.setError(errorResponseError);
        showRetrieveReservationResponse.setErrorShowReservationResponse(errorResponse);
        
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("M083E7632");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        RetrieveReservationResponse roomRetrieveReservationResponse = new RetrieveReservationResponse();
        roomRetrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);

        List<Result<RetrieveReservationResponse>> reservationResponses = new ArrayList<>();
        Result<RetrieveReservationResponse> roomResult = new Result<>(roomRetrieveReservationResponse);
        reservationResponses.add(roomResult);
        Result<RetrieveReservationResponse> showResult = new Result<>(showRetrieveReservationResponse);
        reservationResponses.add(showResult);

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setType(Type.PACKAGE);
        orderEntity.setVersion(Version.V2);
        orderEntity.setLineItems(new ArrayList<>());
        

        Order orderDto = new Order();
        orderDto.setCartId(orderEntity.getCartId());
        orderDto.setId(orderEntity.getId());
        orderDto.setVersion(com.mgmresorts.order.dto.services.Version.V2);
        orderDto.setType(com.mgmresorts.order.dto.services.Type.PACKAGE);
        
        List<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem itemOne = new OrderLineItem();
        itemOne.setOrderLineItemId("OrderLineItemId1");
        itemOne.setCartLineItemId("CartLineItemId1");
        itemOne.setProductType(OrderLineItem.ProductType.SHOW);
        itemOne.setStatus(OrderLineItem.Status.FAILURE);
        // set error message that is returned from SBS
        Message message = new Message();
        SourceSystemError error = new SourceSystemError();
        error.setSourceSystemMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        error.setSourceSystemCode("620-2-214");
        message.setSourceSystemError(error);
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        itemOne.setMessages(messageList);
        orderLineItems.add(itemOne);
        // -----------------------------
        // set product that will succeed
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("OrderLineItemId2");
        itemTwo.setCartLineItemId("CartLineItemId2");
        itemTwo.setProductType(OrderLineItem.ProductType.ROOM);
        itemTwo.setStatus(OrderLineItem.Status.SUCCESS);
        orderLineItems.add(itemTwo);
        // ----------------------------------------------
        orderDto.setOrderLineItems(orderLineItems);
        orderDto.setStatus(Status.PARTIAL);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = createPackageV2Order();
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                executor.invoke((List<ReservationTask>) any);
                result = reservationResponses;
            }
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = orderDto;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("M083E7632", "John", "Doe", ReservationType.ROOM, false, null);

        //Room
        assertEquals("M083E7632", retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName());

        //Show
        assertEquals("620-2-214", retrieveBookingResponse.getErrorShowReservationResponse().getError().getCode());
        assertEquals("<_reservation_not_found>[ Not able to retrieve reservation with given information ]",
                retrieveBookingResponse.getErrorShowReservationResponse().getError().getMessage());
        assertNull(retrieveBookingResponse.getShowReservationResponse());

        assertNotNull(retrieveBookingResponse.getOrder());
        assertEquals("V2", retrieveBookingResponse.getOrder().getVersion().value());
        assertEquals("PACKAGE", retrieveBookingResponse.getOrder().getType().value());
        
        assertEquals("FAILURE", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-214", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("SUCCESS", retrieveBookingResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("PARTIAL", retrieveBookingResponse.getOrder().getStatus().value());
        
        assertNull(retrieveBookingResponse.getPaymentSessionId());
        assertTrue(retrieveBookingResponse.getIsPackage());
    }
    
    @Test
    void getReservationPackageV2CompleteFailureTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                     @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                     @Injectable OrderTransformer orderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                     @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                     @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                     @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory, @Injectable IOrderAccess orderAccess
    ) throws AppException, HttpFailureException {

        RetrieveReservationResponse showRetrieveReservationResponse = new RetrieveReservationResponse();
        com.mgmresorts.sbs.model.ErrorResponse sbsErrorResponse = new com.mgmresorts.sbs.model.ErrorResponse();
        com.mgmresorts.sbs.model.ErrorResponseError sbsErrorResponseError = new com.mgmresorts.sbs.model.ErrorResponseError();
        sbsErrorResponseError.setCode( "620-2-214");
        sbsErrorResponseError.setMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        sbsErrorResponse.setError(sbsErrorResponseError);
        showRetrieveReservationResponse.setErrorShowReservationResponse(sbsErrorResponse);
        

        RetrieveReservationResponse roomRetrieveReservationResponse = new RetrieveReservationResponse();
        com.mgmresorts.rbs.model.ErrorResponse rbsErrorResponse = new com.mgmresorts.rbs.model.ErrorResponse();
        com.mgmresorts.rbs.model.ErrorResponseError rbsErrorResponseError = new com.mgmresorts.rbs.model.ErrorResponseError();
        rbsErrorResponseError.setCode( "632-2-140");
        rbsErrorResponseError.setMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        rbsErrorResponse.setError(rbsErrorResponseError);
        roomRetrieveReservationResponse.setErrorRoomReservationResponse(rbsErrorResponse);

        List<Result<RetrieveReservationResponse>> reservationResponses = new ArrayList<>();
        Result<RetrieveReservationResponse> roomResult = new Result<>(roomRetrieveReservationResponse);
        reservationResponses.add(roomResult);
        Result<RetrieveReservationResponse> showResult = new Result<>(showRetrieveReservationResponse);
        reservationResponses.add(showResult);

        com.mgmresorts.order.entity.Order orderEntity = new com.mgmresorts.order.entity.Order();
        orderEntity.setId("orderId");
        orderEntity.setType(Type.PACKAGE);
        orderEntity.setVersion(Version.V2);
        orderEntity.setLineItems(new ArrayList<>());
        

        Order orderDto = new Order();
        orderDto.setCartId(orderEntity.getCartId());
        orderDto.setId(orderEntity.getId());
        orderDto.setVersion(com.mgmresorts.order.dto.services.Version.V2);
        orderDto.setType(com.mgmresorts.order.dto.services.Type.PACKAGE);
        
        List<OrderLineItem> orderLineItems = new ArrayList<>();
        // set product that will fail
        OrderLineItem itemOne = new OrderLineItem();
        itemOne.setOrderLineItemId("OrderLineItemId1");
        itemOne.setCartLineItemId("CartLineItemId1");
        itemOne.setProductType(OrderLineItem.ProductType.SHOW);
        itemOne.setStatus(OrderLineItem.Status.FAILURE);
        // set error message that is returned from SBS
        Message message1 = new Message();
        SourceSystemError error1 = new SourceSystemError();
        error1.setSourceSystemMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        error1.setSourceSystemCode("620-2-214");
        message1.setSourceSystemError(error1);
        List<Message> messageList1 = new ArrayList<>();
        messageList1.add(message1);
        itemOne.setMessages(messageList1);
        orderLineItems.add(itemOne);
        // -----------------------------
        // set product that will fail
        OrderLineItem itemTwo = new OrderLineItem();
        itemTwo.setOrderLineItemId("OrderLineItemId2");
        itemTwo.setCartLineItemId("CartLineItemId2");
        itemTwo.setProductType(OrderLineItem.ProductType.ROOM);
        itemTwo.setStatus(OrderLineItem.Status.FAILURE);
        // set error message that is returned from RBS
        Message message2 = new Message();
        SourceSystemError error2 = new SourceSystemError();
        error2.setSourceSystemMessage("<_reservation_not_found>[ Not able to retrieve reservation with given information ]");
        error2.setSourceSystemCode("632-2-140");
        message2.setSourceSystemError(error2);
        List<Message> messageList2 = new ArrayList<>();
        messageList2.add(message2);
        itemTwo.setMessages(messageList2);
        orderLineItems.add(itemTwo);
        // ----------------------------------------------
        orderDto.setOrderLineItems(orderLineItems);
        orderDto.setStatus(Status.FAILURE);

        new Expectations() {
            {
                orderAccess.read(anyString);
                result = createPackageV2Order();
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                executor.invoke((List<ReservationTask>) any);
                result = reservationResponses;
            }
            {
                orderAccess.read(anyString);
                result = orderEntity;
            }
            {
                orderTransformer.toLeft((com.mgmresorts.order.entity.Order) any);
                result = orderDto;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("M083E7632", "John", "Doe", ReservationType.ROOM, false, null);

        //Show
        assertEquals("620-2-214", retrieveBookingResponse.getErrorShowReservationResponse().getError().getCode());
        assertEquals("<_reservation_not_found>[ Not able to retrieve reservation with given information ]",
                retrieveBookingResponse.getErrorShowReservationResponse().getError().getMessage());
        assertNull(retrieveBookingResponse.getShowReservationResponse());
        
        //Room
        assertEquals("632-2-140", retrieveBookingResponse.getErrorRoomReservationResponse().getError().getCode());
        assertEquals("<_reservation_not_found>[ Not able to retrieve reservation with given information ]",
                retrieveBookingResponse.getErrorRoomReservationResponse().getError().getMessage());
        assertNull(retrieveBookingResponse.getRoomReservationResponse());

        assertNotNull(retrieveBookingResponse.getOrder());
        assertEquals("V2", retrieveBookingResponse.getOrder().getVersion().value());
        assertEquals("PACKAGE", retrieveBookingResponse.getOrder().getType().value());
        
        assertEquals("FAILURE", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getStatus().toString());
        assertEquals("620-2-214", retrieveBookingResponse.getOrder().getOrderLineItems().get(0).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("FAILURE", retrieveBookingResponse.getOrder().getOrderLineItems().get(1).getStatus().toString());
        assertEquals("632-2-140", retrieveBookingResponse.getOrder().getOrderLineItems().get(1).getMessages().get(0).getSourceSystemError().getSourceSystemCode());
        assertEquals("FAILURE", retrieveBookingResponse.getOrder().getStatus().value());
        
        assertNull(retrieveBookingResponse.getPaymentSessionId());
        assertTrue(retrieveBookingResponse.getIsPackage());
    }

    @Test
    void getReservationRoomTest_ExistingPaymentSessionId(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        GetRoomReservationResponse getRoomReservationResponse = new GetRoomReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        getRoomReservationResponse.setRoomReservation(roomReservationResponse);
        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setRoomReservationResponse(roomReservationResponse);
        retrieveReservationResponse.setPaymentSessionId("1234");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.getReservation(anyString, anyString, anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("1234", "John", "Doe", ReservationType.ROOM, true, "1234");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }

    @Test
    void getReservationShowTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer,
                                @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler,
                                @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                @Injectable ReservationHandlerFactory factory

    ) throws AppException, HttpFailureException {
        ShowReservationResponse showReservationResponse = new ShowReservationResponse();
        showReservationResponse.setConfirmationNumber("1234");
        showReservationResponse.setProfile(new com.mgmresorts.sbs.model.ReservationProfile());
        showReservationResponse.getProfile().setFirstName("John");
        showReservationResponse.getProfile().setLastName("Doe");
        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setShowReservationResponse(showReservationResponse);
        retrieveReservationResponse.setPaymentSessionId("1234");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = showHandler;
            }

            {
                showHandler.getReservation(anyString,anyString,anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("1234", "John", "Doe", ReservationType.SHOW, true, null);
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }
    
    @Test
    void getReservationShowTest_ExistingPaymentSessionId(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer,
                                @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler,
                                @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                @Injectable ReservationHandlerFactory factory

    ) throws AppException, HttpFailureException {
        ShowReservationResponse showReservationResponse = new ShowReservationResponse();
        showReservationResponse.setConfirmationNumber("1234");
        showReservationResponse.setProfile(new com.mgmresorts.sbs.model.ReservationProfile());
        showReservationResponse.getProfile().setFirstName("John");
        showReservationResponse.getProfile().setLastName("Doe");
        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setShowReservationResponse(showReservationResponse);
        retrieveReservationResponse.setPaymentSessionId("1234");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = showHandler;
            }

            {
                showHandler.getReservation(anyString,anyString,anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("1234", "John", "Doe", ReservationType.SHOW, true, "1234");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getShowReservationResponse().getProfile().getLastName(), "Doe");
        assertEquals(retrieveBookingResponse.getPaymentSessionId(), "1234");
    }

    @Test
    void getReservationDiningTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                  @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                  @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                  @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                  @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess,
                                  @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory

    ) throws AppException, HttpFailureException {
        SearchReservationResponse searchReservationResponse = new SearchReservationResponse();
        SearchReservationResponseRestaurantReservationList searchReservationResponseRestaurantReservationList = new SearchReservationResponseRestaurantReservationList();
        searchReservationResponseRestaurantReservationList.setConfirmationNumber("1234");
        searchReservationResponseRestaurantReservationList.setFirstName("John");
        searchReservationResponseRestaurantReservationList.setLastName("Doe");
        List<SearchReservationResponseRestaurantReservationList> reservationLists = new ArrayList<>();
        reservationLists.add(searchReservationResponseRestaurantReservationList);
        searchReservationResponse.setRestaurantReservationList(reservationLists);
        RetrieveReservationResponse retrieveReservationResponse = new RetrieveReservationResponse();
        retrieveReservationResponse.setDiningReservationResponse(searchReservationResponse);

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = diningHandler;
            }
            {
                diningHandler.getReservation(anyString, anyString, anyString, anyBoolean, anyString);
                result = retrieveReservationResponse;
            }
        };

        RetrieveReservationResponse retrieveBookingResponse = reservationService.getReservation("1234", "John", "Doe", ReservationType.DINING, true, null);
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getConfirmationNumber(), "1234");
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getFirstName(), "John");
        assertEquals(retrieveBookingResponse.getDiningReservationResponse().getRestaurantReservationList().get(0).getLastName(), "Doe");
    }

    @Test
    void previewRoomReservationTest_Successful(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                               @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                               @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                               @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                               @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        PreviewReservationResponse previewReservationResponse = new PreviewReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("90505650650");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        previewReservationResponse.setRoomReservationResponse(roomReservationResponse);
        previewReservationResponse.setPaymentSessionId("12345678");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = previewReservationResponse;
            }
        };

        PreviewReservationResponse previewReservationResponseActual = reservationService.previewReservation(createPreviewRoomReservationRequest());
        assertEquals("90505650650", previewReservationResponseActual.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", previewReservationResponseActual.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", previewReservationResponseActual.getRoomReservationResponse().getProfile().getLastName());
        assertEquals("12345678", previewReservationResponseActual.getPaymentSessionId());
    }

    @Test
    void previewRoomReservationTestPackageV2_Successful(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                               @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                               @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                               @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                               @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        PreviewReservationResponse previewReservationResponse = new PreviewReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("90505650650");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");
        roomReservationResponse.setIsStayDateModifiable(true);
        previewReservationResponse.setRoomReservationResponse(roomReservationResponse);
        previewReservationResponse.setPaymentSessionId("12345678");

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                orderConfirmationAccess.getOrderByConfirmationNumber(anyString);
                result = getOrderConfirmationMapping();
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = previewReservationResponse;
            }
        };

        PreviewReservationResponse previewReservationResponseActual = reservationService.previewReservation(createPreviewRoomReservationRequest());
        assertEquals("90505650650", previewReservationResponseActual.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", previewReservationResponseActual.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", previewReservationResponseActual.getRoomReservationResponse().getProfile().getLastName());
        assertEquals("12345678", previewReservationResponseActual.getPaymentSessionId());
        assertFalse(previewReservationResponseActual.getRoomReservationResponse().isIsStayDateModifiable());
    }

    @Test
    void previewRoomReservationTest_RBS4xxException(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                    @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                    @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                    @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                                    @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        PreviewReservationResponse previewReservationResponse = new PreviewReservationResponse();
        previewReservationResponse.setRoomReservationResponse(null);
        ErrorResponse errorResponse = new ErrorResponse();
        ErrorResponseError errorResponseError = new ErrorResponseError();
        errorResponseError.setCode("632-2-146");
        errorResponseError.setMessage("<_dates_not_available>[ One of more dates are not available ]");
        errorResponse.setError(errorResponseError);
        previewReservationResponse.setErrorRoomReservationResponse(errorResponse);

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = previewReservationResponse;
            }
        };

        PreviewReservationResponse previewReservationResponseActual = reservationService.previewReservation(createPreviewRoomReservationRequest());
        assertNotNull(previewReservationResponseActual);
        assertNull(previewReservationResponseActual.getRoomReservationResponse());
        assertNotNull(previewReservationResponseActual.getErrorRoomReservationResponse());
        assertEquals("632-2-146", previewReservationResponseActual.getErrorRoomReservationResponse().getError().getCode());
        assertEquals("<_dates_not_available>[ One of more dates are not available ]", previewReservationResponseActual.getErrorRoomReservationResponse().getError().getMessage());
    }

    @Test
    void previewRoomReservationTest_RBSUnknownException(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                        @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                        @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                        @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        PreviewReservationRequest previewReservationRequest = createPreviewRoomReservationRequest();

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = new AppException(1000 , "Could not get room reservation preview. Unexpected error occurred.");
            }
        };

        assertThrows(AppException.class,() -> reservationService.previewReservation(previewReservationRequest));
    }

    @Test
    void previewRoomReservationTest_PSM4xxException(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                    @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                    @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                    @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        PreviewReservationResponse previewReservationResponse = new PreviewReservationResponse();
        previewReservationResponse.setRoomReservationResponse(null);
        SessionError sessionError = new SessionError();
        sessionError.setErrorCode("702-2-105");
        sessionError.setErrorMessage("Session could not be updated");
        previewReservationResponse.setErrorPaymentSessionResponse(sessionError);

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = previewReservationResponse;

            }
        };

        PreviewReservationRequest previewReservationRequest = createPreviewRoomReservationRequest();
        PreviewReservationResponse previewReservationResponseActual = reservationService.previewReservation(previewReservationRequest);
        assertNotNull(previewReservationResponseActual);
        assertNull(previewReservationResponseActual.getRoomReservationResponse());
        assertNotNull(previewReservationResponseActual.getErrorPaymentSessionResponse());
        assertEquals("702-2-105", previewReservationResponseActual.getErrorPaymentSessionResponse().getErrorCode());
        assertEquals("Session could not be updated", previewReservationResponseActual.getErrorPaymentSessionResponse().getErrorMessage());
    }

    @Test
    void previewRoomReservationTest_PSMUknownException(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                                       @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                       @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                       @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {

        new Expectations() {
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.previewReservation((PreviewReservationRequest) any);
                result = new AppException(1000 , "Could not call Update enablePaymentSession. Unexpected error occurred.");
            }
        };

        PreviewReservationRequest previewReservationRequest = createPreviewRoomReservationRequest();
        assertThrows(AppException.class,() -> reservationService.previewReservation(previewReservationRequest));
    }

    @Test
    void cancelReservationTestInvalidInput(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                           @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, HttpFailureException {
        CancelReservationRequest request = new CancelReservationRequest();
        request.setPaymentSessionId(null);
        request.setReservationType(null);
        request.setCancelRoomReservationRequest(null);

        AppException exception = assertThrows(AppException.class, () -> {
            reservationService.cancelReservation(request);
        });
        assertTrue(exception.getDescription().contains("Invalid input. Please check cancel reservation request for all mandatory arguments"));
        assertEquals(Errors.INVALID_REQUEST_INFORMATION, exception.getCode());
    }

    @Test
    void cancelReservationRoomTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                   @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                   @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                   @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler, @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess, @Injectable IPaymentProcessingHandler paymentProcessingHandler,
                                   @Injectable ReservationHandlerFactory factory
    ) throws AppException, HttpFailureException {
        CancelReservationRequest cancelReservationRequest = new CancelReservationRequest();
        CancelRoomReservationV3Request cancelRoomReservationV3Request = new CancelRoomReservationV3Request();
        cancelRoomReservationV3Request.setOverrideDepositForfeit(false);
        cancelRoomReservationV3Request.setCancellationReason("Testing");
        cancelRoomReservationV3Request.setConfirmationNumber("123");
        cancelRoomReservationV3Request.setPropertyId("123");
        cancelRoomReservationV3Request.setFirstName("John");
        cancelRoomReservationV3Request.setLastName("Doe");

        cancelReservationRequest.setPaymentSessionId("123");
        cancelReservationRequest.setReservationType(ReservationType.ROOM);
        cancelReservationRequest.setCancelRoomReservationRequest(cancelRoomReservationV3Request);

        List<Billing> billings = new ArrayList<>();
        Payment payment = new Payment();
        Billing billing = new Billing();
        billing.setPayment(payment);
        billings.add(billing);

        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        final Map<String, PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setConfirmationNumber("123");
        paymentAuthFields.setAmount(100.0);
        paymentAuthFieldsMap.put("123", paymentAuthFields);
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentAuthFieldsMap);
        paymentSessionBaseFields.setBillings(billings);

        OrderConfirmationMapping orderConfirmationMapping = new OrderConfirmationMapping();
        orderConfirmationMapping.setId("1234");
        orderConfirmationMapping.setConfirmationNumber("5678");

        com.mgmresorts.order.entity.Order order = new com.mgmresorts.order.entity.Order();
        List<LineItem> lineItems = new ArrayList<>();
        LineItem lineItem = new LineItem();
        lineItem.setStatus("SUCCESS");
        lineItem.setConfirmationNumber("123");
        lineItems.add(lineItem);
        order.setLineItems(lineItems);

        CancelRoomReservationResponse cancelRoomReservationResponse = new CancelRoomReservationResponse();
        ReservationProfile reservationProfile = new ReservationProfile();
        reservationProfile.setFirstName("John");
        reservationProfile.setLastName("Doe");
        cancelRoomReservationResponse.setRoomReservation(new RoomReservationResponse());
        cancelRoomReservationResponse.getRoomReservation().setConfirmationNumber("1234");
        cancelRoomReservationResponse.getRoomReservation().setProfile(reservationProfile);
        Deposit deposit = new Deposit();
        deposit.setRefundAmount(BigDecimal.valueOf(100.0));
        cancelRoomReservationResponse.getRoomReservation().setDepositDetails(deposit);

        CancelReservationResponse cancelReservationResponse = new CancelReservationResponse();
        RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("1234");
        roomReservationResponse.setProfile(reservationProfile);
        cancelReservationResponse.setRoomReservationResponse(roomReservationResponse);

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults("123");
                result = paymentSessionBaseFields;
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.cancelReservation((CancelReservationRequest) any, (PaymentSessionBaseFields) any);
                result = cancelReservationResponse;
            }
        };

        CancelReservationResponse cancelReservationResponseActual = reservationService.cancelReservation(cancelReservationRequest);
        assertEquals("1234", cancelReservationResponseActual.getRoomReservationResponse().getConfirmationNumber());
        assertEquals("John", cancelReservationResponseActual.getRoomReservationResponse().getProfile().getFirstName());
        assertEquals("Doe", cancelReservationResponseActual.getRoomReservationResponse().getProfile().getLastName());
    }

    @Test
    void cancelReservationRoomRBSErrorTest(@Injectable OAuthTokenRegistry registry, @Injectable CartHandler cartHandler, @Injectable ItineraryHandler itineraryHandler,
                                           @Injectable DiningHandler diningHandler, @Injectable IOrderAccess IOrderAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                           @Injectable OrderTransformer cartToOrderTransformer, @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                           @Injectable ProfileHandler profileHandler, @Injectable OrderFinancialImpactTransformer orderFiTransformer, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                           @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionAccess paymentSessionAccess,
                                           @Injectable IPaymentProcessingHandler paymentProcessingHandler, @Injectable ReservationHandlerFactory factory
    ) throws AppException {
        CancelReservationRequest cancelReservationRequest = new CancelReservationRequest();
        CancelRoomReservationV3Request cancelRoomReservationV3Request = new CancelRoomReservationV3Request();
        cancelRoomReservationV3Request.setOverrideDepositForfeit(false);
        cancelRoomReservationV3Request.setCancellationReason("Testing");
        cancelRoomReservationV3Request.setConfirmationNumber("123");
        cancelRoomReservationV3Request.setPropertyId("123");
        cancelRoomReservationV3Request.setFirstName("John");
        cancelRoomReservationV3Request.setLastName("Doe");

        cancelReservationRequest.setPaymentSessionId("123");
        cancelReservationRequest.setReservationType(ReservationType.ROOM);
        cancelReservationRequest.setCancelRoomReservationRequest(cancelRoomReservationV3Request);

        List<Billing> billings = new ArrayList<>();
        Payment payment = new Payment();
        Billing billing = new Billing();
        billing.setPayment(payment);
        billings.add(billing);

        PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        final Map<String, PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId(null);
        paymentAuthFields.setAuthorizationCode(null);
        paymentAuthFields.setConfirmationNumber("123");
        paymentAuthFields.setAmount(100.0);
        paymentAuthFieldsMap.put("123", paymentAuthFields);
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentAuthFieldsMap);
        paymentSessionBaseFields.setBillings(billings);

        CancelReservationResponse cancelReservationResponse = new CancelReservationResponse();
        ErrorResponse errorResponse = new ErrorResponse();
        ErrorResponseError errorResponseError = new ErrorResponseError();
        errorResponseError.setCode("632-2-242");
        errorResponseError.setMessage("No reservation available for the input confirmation number.");
        errorResponse.setError(errorResponseError);
        cancelReservationResponse.setErrorRoomReservationResponse(errorResponse);

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = paymentSessionBaseFields;
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.cancelReservation((CancelReservationRequest) any, (PaymentSessionBaseFields) any);
                result = cancelReservationResponse;
            }
        };

        CancelReservationResponse cancelReservationResponseActual = reservationService.cancelReservation(cancelReservationRequest);
        assertNotNull(cancelReservationResponseActual);
        assertNull(cancelReservationResponseActual.getRoomReservationResponse());
        assertNotNull(cancelReservationResponseActual.getErrorRoomReservationResponse());
        assertEquals("632-2-242", cancelReservationResponseActual.getErrorRoomReservationResponse().getError().getCode());
        assertEquals("No reservation available for the input confirmation number.", cancelReservationResponseActual.getErrorRoomReservationResponse().getError().getMessage());
    }

    @Test
    void updateRoomReservationTest_AllSuccess(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                              @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                              @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                              @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                              @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                              @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                              @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                              @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                              @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler

    ) throws AppException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        final RoomReservationResponse roomReservationResponse = new RoomReservationResponse();
        roomReservationResponse.setConfirmationNumber("M083E2635");
        roomReservationResponse.setProfile(new ReservationProfile());
        roomReservationResponse.getProfile().setFirstName("John");
        roomReservationResponse.getProfile().setLastName("Doe");

        updateReservationResponse.setPaymentSessionId("12345678");
        updateReservationResponse.setErrorRoomReservationResponse(null);
        updateReservationResponse.setErrorPaymentSessionResponse(null);
        updateReservationResponse.setRoomReservationResponse(roomReservationResponse);

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNull(response.getErrorPaymentSessionResponse());
        assertNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getPaymentSessionId(), "12345678");
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
    }


    @Test
    void updateRoomReservationTest_PSM4xxException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                   @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                   @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                   @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                   @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                   @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                   @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                   @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                   @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {

        final SessionError sessionErrorResponse = new SessionError();
        sessionErrorResponse.setErrorCode("00041-0001-0-0101");
        sessionErrorResponse.setErrorMessage("Session Expired");

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = new SourceAppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "00041-0001-0-0101", "Session Expired", mapper.asJsonString(sessionErrorResponse));
            }
        };

        final UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNotNull(response.getErrorPaymentSessionResponse());
        assertEquals("00041-0001-0-0101", response.getErrorPaymentSessionResponse().getErrorCode());
    }

    @Test
    void updateRoomReservationTest_PSM5xxException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                   @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                   @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                   @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                   @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                   @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                   @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                   @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                   @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {
        final SessionError sessionErrorResponse = new SessionError();
        sessionErrorResponse.setErrorCode("00041-0001-0-0500");
        sessionErrorResponse.setErrorMessage("Internal Server Error");

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = new SourceAppException(ApplicationError.UNABLE_TO_GET_PAYMENT_SESSION, "00041-0001-0-0500", "Internal Server Error", mapper.asJsonString(sessionErrorResponse));
            }
        };

        final UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNotNull(response.getErrorPaymentSessionResponse());
        assertEquals("00041-0001-0-0500", response.getErrorPaymentSessionResponse().getErrorCode());
    }

    @Test
    void updateRoomReservationTest_PSMUnknownException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                       @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                       @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                       @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                       @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                       @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                       @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                       @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                       @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {
        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = new AppException(Errors.UNEXPECTED_SYSTEM, "Unexpected Server Error");
            }
        };

        assertThrows(AppException.class, () -> reservationService.updateReservation(createUpdateRoomReservationRequest()));
    }


    @Test
    void updateRoomReservationTest_PSMNullException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                    @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                    @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                    @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                    @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                    @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                    @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                    @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                    @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {
        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = new AppException(Errors.UNABLE_TO_GET_PAYMENT_SESSION, "Could not get payment session. No response from backend.");
            }
        };

        assertThrows(AppException.class, () -> reservationService.updateReservation(createUpdateRoomReservationRequest()));
    }

    @Test
    void updateRoomReservationTest_RBSCommitPriceChangeException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                                 @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                                 @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                                 @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                                 @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                                 @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                                 @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                                 @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                                 @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, IOException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        final ModifyCommitErrorResponse errorResponseError = mapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response_price_change.json"),
                ModifyCommitErrorResponse.class);
        updateReservationResponse.setPaymentSessionId("12345678");
        updateReservationResponse.setErrorPaymentSessionResponse(null);
        updateReservationResponse.setRoomReservationResponse(errorResponseError.getRoomReservation());
        updateReservationResponse.setErrorRoomReservationResponse(errorResponseError.getError());

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        final UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNotNull(response.getPaymentSessionId());
        assertEquals(response.getPaymentSessionId(), "12345678");
        assertNotNull(response.getRoomReservationResponse());
        assertEquals(response.getRoomReservationResponse().getConfirmationNumber(), "M083E2635");
        assertEquals(response.getRoomReservationResponse().getProfile().getFirstName(), "John");
        assertEquals(response.getRoomReservationResponse().getProfile().getLastName(), "Doe");
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-2-259");
    }

    @Test
    void updateRoomReservationTest_RBSCommit4xxException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                         @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                         @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                         @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                         @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                         @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                         @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                         @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                         @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, IOException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        final ModifyCommitErrorResponse errorResponseError = mapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_400_failure_response.json"),
                ModifyCommitErrorResponse.class);
        updateReservationResponse.setPaymentSessionId("12345678");
        updateReservationResponse.setErrorPaymentSessionResponse(null);
        updateReservationResponse.setRoomReservationResponse(null);
        updateReservationResponse.setErrorRoomReservationResponse(errorResponseError.getError());

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        final UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNotNull(response.getPaymentSessionId());
        assertEquals(response.getPaymentSessionId(), "12345678");
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals(response.getErrorRoomReservationResponse().getCode(), "632-1-101");
    }

    @Test
    void updateRoomReservationTest_RBSCommit5xxException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                         @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                         @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                         @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                         @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                         @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                         @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                         @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                         @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException, IOException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        final ModifyCommitErrorResponse errorResponseError = mapper.readValue(Utils.readFileFromClassPath("data/room_reservation_commit_500_failure_response.json"),
                ModifyCommitErrorResponse.class);
        updateReservationResponse.setPaymentSessionId("12345678");
        updateReservationResponse.setErrorPaymentSessionResponse(null);
        updateReservationResponse.setRoomReservationResponse(null);
        updateReservationResponse.setErrorRoomReservationResponse(errorResponseError.getError());

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        final UpdateReservationResponse response = reservationService.updateReservation(createUpdateRoomReservationRequest());
        assertNotNull(response);
        assertNotNull(response.getPaymentSessionId());
        assertEquals(response.getPaymentSessionId(), "12345678");
        assertNotNull(response.getErrorRoomReservationResponse());
        assertEquals("632-1-100", response.getErrorRoomReservationResponse().getCode());
    }

    @Test
    void updateRoomReservationTest_RBSCommitUnknownException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler, @Injectable IOrderAccess IOrderAccess,
                                                             @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                             @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                             @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                             @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                             @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                             @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                             @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                             @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        updateReservationResponse.setPaymentSessionId("12345678");

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        final UpdateReservationRequest request = createUpdateRoomReservationRequest();
        final ModifyCommitPutRequest modifyCommitPutRequest = request.getModifyRoomReservationRequest();
        modifyCommitPutRequest.setConfirmationNumber("M083E2635");
        request.setModifyRoomReservationRequest(modifyCommitPutRequest);

        final UpdateReservationResponse response = reservationService.updateReservation(request);
        assertNotNull(response);
        assertNotNull(response.getPaymentSessionId());
        assertEquals(response.getPaymentSessionId(), "12345678");
    }

    @Test
    void updateRoomReservationTest_PSMUpdateException(@Injectable OAuthTokenRegistry registry, @Injectable ICartHandler cartHandler,
                                                      @Injectable IOrderAccess IOrderAccess,
                                                      @Injectable IOrderProgressAccess orderProgressAccess, @Injectable IOrderConfirmationAccess orderConfirmationAccess,
                                                      @Injectable ITransformer<Order, com.mgmresorts.order.entity.Order> orderTransformer,
                                                      @Injectable ITransformer<Order, OrderFinancialImpact> orderFiTransformer, @Injectable ReservationHandlerFactory factory,
                                                      @Injectable Executors executors, @Injectable  IProfileHandler profileHandler, @Injectable IItineraryHandler itineraryHandler,
                                                      @Injectable IContentAccess contentAccess, @Injectable Orders orders, @Injectable IPaymentHandler paymentHandler,
                                                      @Injectable IRoomBookingAccess roomBookingAccess, @Injectable IShowBookingAccess showBookingAccess,
                                                      @Injectable IDiningBookingAccess diningBookingAccess, @Injectable IPaymentSessionRoomHandler paymentSessionRoomHandler,
                                                      @Injectable IPaymentSessionShowHandler paymentSessionShowHandler, @Injectable IPaymentProcessingHandler paymentProcessingHandler
    ) throws AppException {
        // room handler response
        final UpdateReservationResponse updateReservationResponse = new UpdateReservationResponse();
        updateReservationResponse.setPaymentSessionId("12345678");

        final SessionError sessionError = new SessionError();
        sessionError.setErrorCode("123-456");
        sessionError.setErrorMessage("Unable to update session");
        updateReservationResponse.setErrorPaymentSessionResponse(sessionError);

        new Expectations() {
            {
                paymentSessionCommonHandler.getPaymentAuthResults(anyString);
                result = getUpdateReservationPaymentSessionBaseFields();
            }
            {
                factory.get((ItemType) any);
                result = roomHandler;
            }
            {
                roomHandler.updateReservation((UpdateReservationRequest) any, (PaymentSessionBaseFields) any);
                result = updateReservationResponse;
            }
        };

        final UpdateReservationRequest request = createUpdateRoomReservationRequest();
        final ModifyCommitPutRequest modifyCommitPutRequest = request.getModifyRoomReservationRequest();
        modifyCommitPutRequest.setConfirmationNumber("M083E2635");
        request.setModifyRoomReservationRequest(modifyCommitPutRequest);

        final UpdateReservationResponse response = reservationService.updateReservation(request);
        assertNotNull(response);
        assertNotNull(response.getPaymentSessionId());
        assertEquals(response.getPaymentSessionId(), "12345678");
        assertNotNull(response.getErrorPaymentSessionResponse());
        assertEquals(response.getErrorPaymentSessionResponse().getErrorCode(), "123-456");
    }

    private UpdateReservationRequest createUpdateRoomReservationRequest() {
        final UpdateReservationRequest request = new UpdateReservationRequest();
        final ModifyCommitPutRequest putRequest = new ModifyCommitPutRequest();
        putRequest.setConfirmationNumber("M083E2635");
        putRequest.setPreviewReservationTotal(1000.00);
        putRequest.setPreviewReservationDeposit(100.00);

        request.setPaymentSessionId("12345678");
        request.setReservationType(ReservationType.ROOM);
        request.setModifyRoomReservationRequest(putRequest);
        return request;
    }

    private List<Billing> getBillingsWithExpiryAndCc() {
        final List<Billing> billings = new ArrayList<>();
        final Billing billing = new Billing();
        final Payment payment = new Payment();
        payment.setExpiry("07/2030");
        payment.setCcToken("4329814172512343290148012341234321");
        billing.setPayment(payment);
        billings.add(billing);
        return billings;
    }

    private PaymentSessionBaseFields getUpdateReservationPaymentSessionBaseFields(){
        final PaymentSessionBaseFields paymentSessionBaseFields = new PaymentSessionBaseFields();
        final Map<String, PaymentAuthFields> paymentAuthFieldsMap = new HashMap<>();
        final PaymentAuthFields paymentAuthFields = new PaymentAuthFields();
        paymentAuthFields.setPaymentId("43243124432");
        paymentAuthFields.setAuthorizationCode("OA14k");
        paymentAuthFields.setAmount(1000.00);
        paymentAuthFields.setConfirmationNumber("M083E2635");
        paymentAuthFieldsMap.put("M083E2635", paymentAuthFields);
        final Map<String, PaymentAuthFields> sessionPreAuthAuthFieldsMap = new HashMap<>();
        final PaymentAuthFields sessionPreAuthAuthFields = new PaymentAuthFields();
        sessionPreAuthAuthFields.setAmount(0.0d);
        sessionPreAuthAuthFieldsMap.put("M083E2635", sessionPreAuthAuthFields);
        paymentSessionBaseFields.setOrderReferenceNumber("M083E2635");
        paymentSessionBaseFields.setPaymentAuthFieldsMap(paymentAuthFieldsMap);
        paymentSessionBaseFields.setBillings(getBillingsWithExpiryAndCc());
        return paymentSessionBaseFields;
    }

    private PreviewReservationRequest createPreviewRoomReservationRequest() {
        PreviewReservationRequest request = new PreviewReservationRequest();
        request.setPaymentSessionId("12345678");
        request.setReservationType(ReservationType.ROOM);
        request.setPreviewRoomReservationRequest(createRoomReservationPreviewRequest());
        return request;
    }

    private PremodifyPutRequest createRoomReservationPreviewRequest() {
        PremodifyPutRequest request = new PremodifyPutRequest();
        request.setConfirmationNumber("M083E2631");
        request.setFirstName("Raman");
        request.lastName("Lamba");
        PremodifyPutRequestTripDetails tripDetails = new PremodifyPutRequestTripDetails();
        tripDetails.setCheckInDate("2025-02-16");
        tripDetails.setCheckOutDate("2025-02-18");
        request.setTripDetails(tripDetails);
        request.setRoomRequests(new ArrayList<>());
        return request;
    }

    private OrderConfirmationMapping getOrderConfirmationMapping() {
        final OrderConfirmationMapping orderConfirmationMapping = new OrderConfirmationMapping();
        orderConfirmationMapping.setConfirmationNumber("M083E7632");
        orderConfirmationMapping.setId("123456");
        orderConfirmationMapping.setVersion(Version.V2);
        orderConfirmationMapping.setType(Type.PACKAGE);
        return orderConfirmationMapping;
    }
}