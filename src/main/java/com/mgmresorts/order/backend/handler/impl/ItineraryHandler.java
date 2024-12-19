package com.mgmresorts.order.backend.handler.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import javax.inject.Inject;

import com.mgmresorts.rbs.model.CalculateRoomChargesResponse;
import org.apache.commons.lang3.StringUtils;

import com.mgmresorts.common.config.Runtime;
import com.mgmresorts.common.dto.services.OutHeaderSupport;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.http.HttpFailureException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.notification.Email;
import com.mgmresorts.common.notification.Emailer;
import com.mgmresorts.common.notification.SmtpEmailer;
import com.mgmresorts.common.utils.Dates;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.itineraries.dto.client.itinerary.InItinerary;
import com.mgmresorts.itineraries.dto.client.itinerary.ItineraryData;
import com.mgmresorts.itineraries.dto.client.itinerary.TripParams;
import com.mgmresorts.itineraries.dto.client.services.CreateItineraryRequest;
import com.mgmresorts.itineraries.dto.client.services.ItineraryResponse;
import com.mgmresorts.order.backend.access.CommonConfig;
import com.mgmresorts.order.backend.access.IItineraryAccess;
import com.mgmresorts.order.backend.handler.IItineraryHandler;
import com.mgmresorts.order.dto.GuestProfile;
import com.mgmresorts.order.errors.Errors;
import com.mgmresorts.rbs.model.TripDetails;
import com.mgmresorts.sbs.model.ShowChargesResponse;
import com.mgmresorts.shopping.cart.dto.Cart;
import com.mgmresorts.shopping.cart.dto.DiningSelectionDetails;
import com.mgmresorts.shopping.cart.dto.ItemType;

public class ItineraryHandler implements IItineraryHandler {
    
    private final Logger logger = Logger.get(ItineraryHandler.class);
    private final JSonMapper mapper = new JSonMapper();
    
    @Inject
    private IItineraryAccess itineraryAccess;
    @Inject
    private SmtpEmailer smtpEmailer;

    public String create(GuestProfile profile, Cart cart) throws AppException {
        try {
            final String response = itineraryAccess.createItinerary(createItineraryRequest(profile, createCartTripDetails(cart)));
            if (Utils.isEmpty(response)) {
                throw new AppException(Errors.UNABLE_TO_CREATE_ITINERARY, profile.getId() + "|" + profile.getMlifeNo());
            }
            
            final ItineraryData itineraryData = mapper.readValue(response, ItineraryResponse.class).getItinerary();
            profile.setItineraryData(itineraryData);
            return itineraryData.getItineraryId();
        } catch (HttpFailureException e) {
            if (e.getHttpCode() <= 500) {
                String message = null;
                String code = null;
                if (!Utils.isEmpty(e.getPayload()) && Utils.isValidJson(e.getPayload())) {
                    logger.error("[Error from Itinerary] could not create itinerary :  {}", e.getPayload());
                    final OutHeaderSupport errorResponse = mapper.readValue(e.getPayload(), OutHeaderSupport.class);
                    if (errorResponse.getHeader() != null && errorResponse.getHeader().getStatus() != null && !Utils.isEmpty(errorResponse.getHeader().getStatus().getMessages())) {
                        message = errorResponse.getHeader().getStatus().getMessages().get(0).getText();
                        code = errorResponse.getHeader().getStatus().getMessages().get(0).getCode();
                    } else {
                        message = e.getMessage();
                    }
                    if (StringUtils.isNotBlank(code) && (code.equalsIgnoreCase("120-2-3004") || code.equalsIgnoreCase("120-2-3010")
                            || code.equalsIgnoreCase("120-3-3013") || code.equalsIgnoreCase("120-3-3014")
                            || code.equalsIgnoreCase("120-3-3015"))) {
                        sendEmail();
                        throw new AppException(Errors.UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID,
                                profile.getId() + "|" + profile.getMlifeNo() + " ; failure message from [Itinerary Services] : " + message, e);
                    }
                }
                throw new AppException(Errors.UNABLE_TO_CREATE_ITINERARY,
                        profile.getId() + "|" + profile.getMlifeNo() + " ; failure message from [Itinerary Services] : " + message, e);
            } else {
                logger.error("[Error from Itinerary Services] Something unexpected happened in create itinerary : " + e.getMessage());
                throw new AppException(SystemError.UNEXPECTED_SYSTEM, e, e.getMessage());
            }
        } catch (AppException e) {
            logger.error("[Error from Itinerary Services] Something unexpected happened in create itinerary :  ", e.getMessage());
            throw new AppException(SystemError.UNEXPECTED_SYSTEM, e.getMessage(), e);
        }
    }

    private CreateItineraryRequest createItineraryRequest(GuestProfile profile, TripDetails tripDetails) {
        final InItinerary inItinerary = new InItinerary();
        inItinerary.setCustomerId(profile.getId());
        inItinerary.setItineraryName(Utils.encodeString(profile.getFirstName()).concat(Utils.encodeString(profile.getLastName())).concat(LocalDate.now().toString()));
        final TripParams tripParams = new TripParams();
        tripParams.setNumAdults(tripDetails.getNumAdults());
        tripParams.setNumChildren(tripDetails.getNumChildren());
        tripParams.setArrivalDate(LocalDate.parse(tripDetails.getCheckInDate(), DateTimeFormatter.ISO_LOCAL_DATE));
        tripParams.setDepartureDate(LocalDate.parse(tripDetails.getCheckOutDate(), DateTimeFormatter.ISO_LOCAL_DATE));
        inItinerary.setTripParams(tripParams);
        final CreateItineraryRequest createItineraryRequest = new CreateItineraryRequest();
        createItineraryRequest.setItinerary(inItinerary);
        return createItineraryRequest;
    }


    private TripDetails createCartTripDetails(final Cart cart) {
        final TripDetails tripDetails = new TripDetails();
        if (!Utils.isEmpty(cart.getCartLineItems())) {
            cart.getCartLineItems().forEach(cartLineItem -> {
                if (cartLineItem.getType() == ItemType.ROOM) {
                    final CalculateRoomChargesResponse roomCharge = mapper.readValue(cartLineItem.getContent(),
                            CalculateRoomChargesResponse.class);
                    final TripDetails details = roomCharge.getTripDetails();
                    if (Utils.isEmpty(tripDetails.getCheckInDate()) || Dates.isBefore(
                            LocalDate.parse(tripDetails.getCheckInDate()), LocalDate.parse(details.getCheckInDate()))) {
                        tripDetails.setCheckInDate(details.getCheckInDate());
                    }

                    if (Utils.isEmpty(tripDetails.getCheckOutDate())
                            || !Dates.isBefore(LocalDate.parse(tripDetails.getCheckOutDate()),
                                    LocalDate.parse(details.getCheckOutDate()))) {
                        tripDetails.setCheckOutDate(details.getCheckOutDate());
                    }

                    if (tripDetails.getNumAdults() == null || details.getNumAdults() > tripDetails.getNumAdults()) {
                        tripDetails.setNumAdults(details.getNumAdults());
                    }

                    if (tripDetails.getNumChildren() == null
                            || details.getNumChildren() > tripDetails.getNumChildren()) {
                        tripDetails.setNumChildren(details.getNumChildren());
                    }
                }
                if (cartLineItem.getType() == ItemType.SHOW) {
                    final ShowChargesResponse showCharge = mapper.readValue(cartLineItem.getContent(), ShowChargesResponse.class);
                    
                    if (Utils.isEmpty(tripDetails.getCheckInDate()) || Dates.isBefore(
                            LocalDate.parse(tripDetails.getCheckInDate()), LocalDate.parse(showCharge.getEventDate()))) {
                        tripDetails.setCheckInDate(showCharge.getEventDate());
                    }

                    if (Utils.isEmpty(tripDetails.getCheckOutDate())
                            || !Dates.isBefore(LocalDate.parse(tripDetails.getCheckOutDate()),
                                    LocalDate.parse(showCharge.getEventDate()))) {
                        tripDetails.setCheckOutDate(showCharge.getEventDate());
                    }

                    if (tripDetails.getNumAdults() == null || showCharge.getTickets().size() > tripDetails.getNumAdults()) {
                        tripDetails.setNumAdults(showCharge.getTickets().size());
                    }

                    tripDetails.setNumChildren(tripDetails.getNumChildren() == null ? 0 : tripDetails.getNumChildren());
                }
                if (cartLineItem.getType() == ItemType.DINING) {
                    final DiningSelectionDetails responses = cartLineItem.getItemSelectionDetails().getDiningSelectionDetails();

                    if (Utils.isEmpty(tripDetails.getCheckInDate()) || Dates.isBefore(
                            LocalDate.parse(tripDetails.getCheckInDate()), responses.getReservationDate())) {
                        tripDetails.setCheckInDate(responses.getReservationDate().toString());
                    }

                    if (Utils.isEmpty(tripDetails.getCheckOutDate())
                            || !Dates.isBefore(LocalDate.parse(tripDetails.getCheckOutDate()),
                            responses.getReservationDate())) {
                        tripDetails.setCheckOutDate(responses.getReservationDate().toString());
                    }

                    if (tripDetails.getNumAdults() == null || responses.getPartySize() > tripDetails.getNumAdults()) {
                        tripDetails.setNumAdults(responses.getPartySize());
                    }

                    tripDetails.setNumChildren(tripDetails.getNumChildren() == null ? 0 : tripDetails.getNumChildren());
                }
            });
        }
        return tripDetails;
    }
    
    private void sendEmail() {
        final String lowerEnvTag = !StringUtils.equalsAnyIgnoreCase(Runtime.get().readableEnvironment(), "PROD") ? "[NON-PROD]" : "";
        try {
            final Email emailReq = Emailer.wrap(lowerEnvTag + "Cart checkout failed due to GSE ID - MGM Rewards mismatch!",
                    Arrays.asList(!Utils.isEmpty(IDENTITY_SEND_EMAIL_TO_LIST) ? IDENTITY_SEND_EMAIL_TO_LIST.split(",") : new String[] { }),
                    Arrays.asList(!Utils.isEmpty(IDENTITY_SEND_EMAIL_CC_LIST) ? IDENTITY_SEND_EMAIL_CC_LIST.split(",") : new String[] { }),
                    Arrays.asList(!Utils.isEmpty(IDENTITY_SEND_EMAIL_BCC_LIST) ? IDENTITY_SEND_EMAIL_BCC_LIST.split(",") : new String[] { }));

            final String mgmId = CommonConfig.getTokenMgmId();
            final String mLifeNumber = CommonConfig.getTokenMlifeNumber();
            final String gseId = CommonConfig.getTokenGseId();
            if (StringUtils.isNotEmpty(mgmId) && StringUtils.isNotEmpty(mLifeNumber) && StringUtils.isNotEmpty(gseId)) {
                final String emailContentBody = createItineraryFailureEmailBody(mgmId, mLifeNumber, gseId);
                logger.info("Itinerary create failure due to GSE ID mismatch email body content generated: {}", emailContentBody);
                emailReq.setHtmlBody(Email.Html.init().body(emailContentBody).build());
                smtpEmailer.send(emailReq);
            }
        } catch (AppException e) {
            logger.error("Exception occurred while sending itinerary create failure email to Identity team: {}", e.getMessage());
        }
    }

    private String createItineraryFailureEmailBody(final String mgmId, final String mlifeId, final String gseId) {
        final String createItineraryFailureEmailTemplate = "<b><u>Mgm Id</u> :</b> %s \n "
                + "<b><u>MLife Number</u> :</b> %s \n <b><u>Invalid GSE Id</u> :</b> %s";

        return String.format(createItineraryFailureEmailTemplate, mgmId, mlifeId, gseId).replace("\n", "<br/><br/>");
    }
}
