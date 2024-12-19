package com.mgmresorts.order.core;

import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.mgmresorts.common.errors.ErrorManager.IError;
import com.mgmresorts.common.guice.BaseWithEventsModule;
import com.mgmresorts.common.transform.ITransformer;
import com.mgmresorts.order.backend.access.ICartAccess;
import com.mgmresorts.order.backend.access.IContentAccess;
import com.mgmresorts.order.backend.access.IDiningBookingAccess;
import com.mgmresorts.order.backend.access.IItineraryAccess;
import com.mgmresorts.order.backend.access.IPIMAccess;
import com.mgmresorts.order.backend.access.IPaymentAccess;
import com.mgmresorts.order.backend.access.IPaymentProcessingAccess;
import com.mgmresorts.order.backend.access.IPaymentSessionAccess;
import com.mgmresorts.order.backend.access.IProfileAccess;
import com.mgmresorts.order.backend.access.IRTCAccess;
import com.mgmresorts.order.backend.access.IRoomBookingAccess;
import com.mgmresorts.order.backend.access.IShowBookingAccess;
import com.mgmresorts.order.backend.access.impl.CartAccess;
import com.mgmresorts.order.backend.access.impl.ContentAccess;
import com.mgmresorts.order.backend.access.impl.DiningBookingAccess;
import com.mgmresorts.order.backend.access.impl.ItineraryAccess;
import com.mgmresorts.order.backend.access.impl.PIMAccess;
import com.mgmresorts.order.backend.access.impl.PaymentAccess;
import com.mgmresorts.order.backend.access.impl.PaymentProcessingAccess;
import com.mgmresorts.order.backend.access.impl.PaymentSessionAccess;
import com.mgmresorts.order.backend.access.impl.ProfileAccess;
import com.mgmresorts.order.backend.access.impl.RTCAccess;
import com.mgmresorts.order.backend.access.impl.RoomBookingAccess;
import com.mgmresorts.order.backend.access.impl.ShowBookingAccess;
import com.mgmresorts.order.backend.handler.ICartHandler;
import com.mgmresorts.order.backend.handler.IItineraryHandler;
import com.mgmresorts.order.backend.handler.ILogHandler;
import com.mgmresorts.order.backend.handler.IPaymentHandler;
import com.mgmresorts.order.backend.handler.IPaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionRoomHandler;
import com.mgmresorts.order.backend.handler.IPaymentSessionShowHandler;
import com.mgmresorts.order.backend.handler.IProfileHandler;
import com.mgmresorts.order.backend.handler.impl.CartHandler;
import com.mgmresorts.order.backend.handler.impl.ItineraryHandler;
import com.mgmresorts.order.backend.handler.impl.LogHandler;
import com.mgmresorts.order.backend.handler.impl.PaymentHandler;
import com.mgmresorts.order.backend.handler.impl.PaymentProcessingHandler;
import com.mgmresorts.order.backend.handler.impl.PaymentSessionCommonHandler;
import com.mgmresorts.order.backend.handler.impl.PaymentSessionRoomHandler;
import com.mgmresorts.order.backend.handler.impl.PaymentSessionShowHandler;
import com.mgmresorts.order.backend.handler.impl.ProfileHandler;
import com.mgmresorts.order.database.access.IOrderAccess;
import com.mgmresorts.order.database.access.IOrderConfirmationAccess;
import com.mgmresorts.order.database.access.IOrderProgressAccess;
import com.mgmresorts.order.database.access.impl.OrderAccess;
import com.mgmresorts.order.database.access.impl.OrderConfirmationAccess;
import com.mgmresorts.order.database.access.impl.OrderProgressAccess;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.entity.Order;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.order.event.EventFactory;
import com.mgmresorts.order.event.IEventHandler;
import com.mgmresorts.order.event.InEventType;
import com.mgmresorts.order.event.handler.ShowReservationConfirmationEventHandler;
import com.mgmresorts.order.event.handler.ShowReservationFailureEventHandler;
import com.mgmresorts.order.logging.OrderFinancialImpact;
import com.mgmresorts.order.service.IOrderEventingService;
import com.mgmresorts.order.service.IOrderService;
import com.mgmresorts.order.service.IReservationService;
import com.mgmresorts.order.service.consumer.IMergeConsumer;
import com.mgmresorts.order.service.consumer.impl.MergeConsumer;
import com.mgmresorts.order.service.impl.OrderEventingService;
import com.mgmresorts.order.service.impl.OrderService;
import com.mgmresorts.order.service.impl.ReservationService;
import com.mgmresorts.order.service.task.IProductHandler;
import com.mgmresorts.order.service.task.handler.DiningHandler;
import com.mgmresorts.order.service.task.handler.RoomHandler;
import com.mgmresorts.order.service.task.handler.ShowHandler;
import com.mgmresorts.order.service.transformer.AddOnComponentTransformer;
import com.mgmresorts.order.service.transformer.AgentInfoTransformer;
import com.mgmresorts.order.service.transformer.DeliveryMethodTransformer;
import com.mgmresorts.order.service.transformer.DiningAgentInfoTransformer;
import com.mgmresorts.order.service.transformer.OrderCheckoutEmailEventTransformer;
import com.mgmresorts.order.service.transformer.OrderFinancialImpactTransformer;
import com.mgmresorts.order.service.transformer.OrderTransformer;
import com.mgmresorts.order.service.transformer.RoomBillingTransformer;
import com.mgmresorts.order.service.transformer.RoomProfileTransformer;
import com.mgmresorts.order.service.transformer.ShowBillingTransformer;
import com.mgmresorts.order.service.transformer.ShowProfileTransformer;
import com.mgmresorts.rtc.RtcReservationEvent;
import com.mgmresorts.shopping.cart.dto.ItemType;

public class Module extends BaseWithEventsModule {

    @Override
    protected void bindApplicationBeans() {
        bind(IError.class).to(Errors.class).in(Scopes.SINGLETON);
        bind(EventFactory.class).toInstance(new EventFactory());
        bind(IOrderService.class).to(OrderService.class).in(Scopes.SINGLETON);
        bind(IReservationService.class).to(ReservationService.class).in(Scopes.SINGLETON);
        bind(IItineraryHandler.class).to(ItineraryHandler.class).in(Scopes.SINGLETON);
        bind(IProfileHandler.class).to(ProfileHandler.class).in(Scopes.SINGLETON);
        bind(ICartHandler.class).to(CartHandler.class).in(Scopes.SINGLETON);
        bind(ILogHandler.class).to(LogHandler.class).in(Scopes.SINGLETON);
        bind(IContentAccess.class).to(ContentAccess.class).in(Scopes.SINGLETON);
        bind(IPIMAccess.class).to(PIMAccess.class).in(Scopes.SINGLETON);
        bind(IRoomBookingAccess.class).to(RoomBookingAccess.class).in(Scopes.SINGLETON);
        bind(IShowBookingAccess.class).to(ShowBookingAccess.class).in(Scopes.SINGLETON);
        bind(IPaymentHandler.class).to(PaymentHandler.class).in(Scopes.SINGLETON);
        bind(IPaymentSessionRoomHandler.class).to(PaymentSessionRoomHandler.class).in(Scopes.SINGLETON);
        bind(IPaymentSessionShowHandler.class).to(PaymentSessionShowHandler.class).in(Scopes.SINGLETON);
        bind(IPaymentSessionCommonHandler.class).to(PaymentSessionCommonHandler.class).in(Scopes.SINGLETON);
        bind(IPaymentProcessingAccess.class).to(PaymentProcessingAccess.class).in(Scopes.SINGLETON);
        bind(IPaymentProcessingHandler.class).to(PaymentProcessingHandler.class).in(Scopes.SINGLETON);
        bind(IPaymentSessionAccess.class).to(PaymentSessionAccess.class).in(Scopes.SINGLETON);
        bind(IMergeConsumer.class).to(MergeConsumer.class).in(Scopes.SINGLETON);
        bind(IDiningBookingAccess.class).to(DiningBookingAccess.class).in(Scopes.SINGLETON);
        bind(IItineraryAccess.class).to(ItineraryAccess.class).in(Scopes.SINGLETON);
        bind(IProfileAccess.class).to(ProfileAccess.class).in(Scopes.SINGLETON);
        bind(IPaymentAccess.class).to(PaymentAccess.class).in(Scopes.SINGLETON);
        bind(ICartAccess.class).to(CartAccess.class).in(Scopes.SINGLETON);
        bind(IRTCAccess.class).to(RTCAccess.class).in(Scopes.SINGLETON);
        bind(IOrderAccess.class).to(OrderAccess.class).in(Scopes.SINGLETON);
        bind(IOrderProgressAccess.class).to(OrderProgressAccess.class).in(Scopes.SINGLETON);
        bind(IOrderConfirmationAccess.class).to(OrderConfirmationAccess.class).in(Scopes.SINGLETON);
        
        bind(new TypeLiteral<ITransformer<com.mgmresorts.order.dto.services.Order, Order>>() {
        }).to(OrderTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.rbs.model.RoomReservationRequestAgentInfo>>() {
        }).to(AgentInfoTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.shopping.cart.dto.AgentInfo, com.mgmresorts.dbs.model.CreateReservationRequestRestaurantReservationAgentInfo>>() {
        }).to(DiningAgentInfoTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.rbs.model.RoomBillingDetails>>() {
        }).to(RoomBillingTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.order.dto.Billing, com.mgmresorts.sbs.model.BillingInfo>>() {
        }).to(ShowBillingTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<GuestProfile, com.mgmresorts.rbs.model.ReservationProfile>>() {
        }).to(RoomProfileTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<GuestProfile, com.mgmresorts.sbs.model.ReservationProfile>>() {
        }).to(ShowProfileTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.order.entity.OrderEvent, RtcReservationEvent>>() {
        }).to(OrderCheckoutEmailEventTransformer.class).in(Scopes.SINGLETON);


        bind(new TypeLiteral<ITransformer<com.mgmresorts.order.dto.services.Order, OrderFinancialImpact>>() {
        }).to(OrderFinancialImpactTransformer.class).in(Scopes.SINGLETON);

        bind(new TypeLiteral<ITransformer<com.mgmresorts.shopping.cart.dto.AddOnComponent, com.mgmresorts.order.dto.AddOnComponent>>() {
        }).to(AddOnComponentTransformer.class).in(Scopes.SINGLETON);
        bind(new TypeLiteral<ITransformer<com.mgmresorts.shopping.cart.dto.DeliveryMethod, com.mgmresorts.order.dto.DeliveryMethod>>() {
        }).to(DeliveryMethodTransformer.class).in(Scopes.SINGLETON);

        final MapBinder<ItemType, IProductHandler> sourceAccessbinder = MapBinder.newMapBinder(binder(), ItemType.class, IProductHandler.class);
        sourceAccessbinder.addBinding(ItemType.ROOM).to(RoomHandler.class);
        sourceAccessbinder.addBinding(ItemType.SHOW).to(ShowHandler.class);
        sourceAccessbinder.addBinding(ItemType.DINING).to(DiningHandler.class);

        final MapBinder<InEventType, IEventHandler> binder = MapBinder.newMapBinder(binder(), InEventType.class, IEventHandler.class);
        binder.addBinding(InEventType.SHOW_RESERVATION_CREATE).to(ShowReservationConfirmationEventHandler.class);
        binder.addBinding(InEventType.SHOW_RESERVATION_FAILURE).to(ShowReservationFailureEventHandler.class);
        bind(IOrderEventingService.class).to(OrderEventingService.class).in(Scopes.SINGLETON);
    }
    
    @Override
    protected void preConfigure() {
        // TODO Auto-generated method stub

    }
}
