package com.mgmresorts.order.core;

import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.registry.OAuthTokenRegistry;
import com.mgmresorts.common.security.Jwts;
import com.mgmresorts.common.utils.ThreadContext;

public class EnvironmentSetup {
    public static void init() throws AppException {
        final String endpoint = "https://mgmdmp.oktapreview.com/oauth2/ausph7ezp3Gkkk8WN0h7/v1/token";
        final String user = "mgm_booking_rooms_mock_riddhi@test.mgmresorts.com";
        final String password = null;
        final String clientId = "0oag5qxytqM4HvJHu0h7";
        final String secret = null;
        final String scopes = "rooms.reservation:create rooms.reservation:update rooms.reservation:read rooms.reservation:override rooms.reservation.charges:override "
                + "rooms.reservation.charges:read rooms.availability:read rooms.program:read booking.room.resv:update "
                + "booking.room.resv:search rcs.rmqs:all rcs.rcs:all rcs.ns:all rcs.dls:all booking.room.resv:batch "
                + "profile:read profile:create profile:update directory:read:guest directory:search:guest directory:create:guest "
                + "directory.passcode:issue:guest loyalty:profile:create loyalty:profile:update loyalty:profile:read profile:read "
                + "loyalty:balances:read loyalty:comments:read loyalty:linkedprofiles:read loyalty:stopcodes:read loyalty:promos:read "
                + "loyalty:taxinfo:read itinerary:read abandoned:cart:read itinerary:update itinerary:create itinerary:read "
                + "loyalty:balances:read loyalty:comments:read loyalty:linkedprofiles:read loyalty:promos:read loyalty:stopcodes:read "
                + "loyalty:taxinfo:read loyalty:profile:create loyalty:profile:read loyalty:profile:update restaurants.notes:read "
                + "restaurants.reservation:cancel:guest restaurants.reservation:create restaurants.reservations:read:guest "
                + "restaurants.reservation.cart:create restaurants.reservation:update restaurants.notes:read:guest "
                + "restaurants.reservation.cart:create:guest restaurants.reservations:read restaurants.reservation:update:guest "
                + "restaurants.reservation.sheet:read restaurants.reservation:cancel restaurants.reservation.cart:delete:guest "
                + "restaurants.reservation.cart:delete restaurants.availability:read:guest restaurants.reservation.sheet:read:guest "
                + "restaurants.availability:read restaurants.reservation:create:guest cart:checkout cart:update cart:delete cart:read cart:create";

        init(endpoint, user, password, clientId, secret, scopes);
    }

    public static void init(String endpoint, String user, String password, String clientId, String secret, String scopes) throws AppException {
        System.setProperty("runtime.environment", "local");
        final OAuthTokenRegistry instanceOf = InjectionContext.get().instanceOf(OAuthTokenRegistry.class);
        final String accessToken = instanceOf.getTokenUsingPassword(endpoint, user, password, clientId, secret, scopes);
        ThreadContext.getContext().get().setJwt(Jwts.decode("Bearer " + accessToken));
    }

    public static void init(String endpoint, String client, String secret) throws AppException {
        System.setProperty("runtime.environment", "local");

        final OAuthTokenRegistry instanceOf = InjectionContext.get().instanceOf(OAuthTokenRegistry.class);
        final String accessToken = instanceOf.getAccessToken(endpoint, client, secret, //
                "cart:create cart:checkout cart:read cart:update cart:delete rooms.reservation:create itinerary:create profile:create cart:event:delete");
        ThreadContext.getContext().get().setJwt(Jwts.decode("Bearer " + accessToken));
    }

}
