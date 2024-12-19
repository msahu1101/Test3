package com.mgmresorts.order.errors;

import com.mgmresorts.common.errors.ErrorManager.ErrorConfig;
import com.mgmresorts.common.errors.ErrorManager.IError.Group;
import com.mgmresorts.common.errors.HttpStatus;

public interface ApplicationError {

    String group = "Application";

    @ErrorConfig(group = Group.BUSINESS, message = "No cart was found with cartId: %s and mgmId: %s", httpStatus = HttpStatus.NOT_FOUND)
    int NO_CART_FOUND = 3001;

    @ErrorConfig(group = Group.BUSINESS, message = "The cart with cartId: %s and mgmId %s is empty. There are no products to checkout.", httpStatus = HttpStatus.NOT_FOUND)
    int EMPTY_CART = 3002;

    @ErrorConfig(group = Group.BUSINESS, message = "Required data was missing from the request payload: %s", httpStatus = HttpStatus.BAD_REQUEST)
    int INVALID_REQUEST = 3003;

    @ErrorConfig(group = Group.BUSINESS, message = "The charge amount is less than the amount due.", httpStatus = HttpStatus.PAYMENT_REQUIRED)
    int INSUFFICIENT_CHARGE_AMOUNT = 3004;

    @ErrorConfig(group = Group.BUSINESS, message = "Room cannot be reserved.")
    int UNABLE_TO_BOOK_ROOM = 3005;

    @ErrorConfig(group = Group.BACKEND, message = "Could not create itinerary for customer id|mlife : %s", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CREATE_ITINERARY = 3006;

    @ErrorConfig(group = Group.BACKEND, message = "Could not create a customer profile: %s", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CREATE_CUSTOMER_PROFILE = 3007;

    @ErrorConfig(group = Group.BACKEND, message = "Could not retrieve the cart: %s", httpStatus = HttpStatus.NOT_FOUND)
    int UNABLE_TO_GET_CART = 3008;

    @ErrorConfig(group = Group.BACKEND, message = "Failed to acquire a token: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int IDENTITY_SERVICE_FAILURE = 3009;

    @ErrorConfig(group = Group.BUSINESS, message = "Cart is not eligible for checkout.", httpStatus = HttpStatus.PRECONDITION_FAILED)
    int NO_CHECKOUT_ELIGIBLE = 3010;

    @ErrorConfig(group = Group.BACKEND, message = "Could not move cart item to new the cart: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int UNABLE_TO_MOVE_PRODUCT = 3011;

    @ErrorConfig(group = Group.BUSINESS, message = "The cart checkout is already in progress.", httpStatus = HttpStatus.BAD_REQUEST)
    int CHECKOUT_IN_PROGRESS = 3012;

    @ErrorConfig(group = Group.BACKEND, message = "Failure to delete cart copy: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int CART_EVENTS_SERVICE_FAILURE = 3013;

    @ErrorConfig(group = Group.BUSINESS, message = "Cart is not eligible for checkout due to ineligible items. cartId:%s | mgmId:%s", httpStatus = HttpStatus.PRECONDITION_FAILED)
    int NO_CHECKOUT_ELIGIBLE_INELIGIBLE_ITEMS = 3014;

    @ErrorConfig(group = Group.BUSINESS, message = "Show cannot be reserved.")
    int UNABLE_TO_BOOK_SHOW = 3015;

    @ErrorConfig(group = Group.BUSINESS, message = "Show Package is not active.", httpStatus = HttpStatus.PRECONDITION_FAILED)
    int PACKAGE_NOT_ACTIVE = 3016;

    @ErrorConfig(group = Group.BUSINESS, message = "Could not process package cart as it does not have required room and shows. [%s]", httpStatus = HttpStatus.PRECONDITION_FAILED)
    int PACKAGE_CART_NOT_HAVE_REQUIRED_ITEM = 3017;

    @ErrorConfig(group = Group.BUSINESS, message = "Could not process package cart as room reservation failed. Please retry check out.")
    int PACKAGE_CART_ROOM_NOT_SUCCESSFUL = 3018;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to create itinerary. Required gse id is missing for id|mlife : %s", httpStatus = HttpStatus.BAD_REQUEST)
    int NO_GSE_ID_FOUND = 3019;

    @ErrorConfig(group = Group.BUSINESS, message = "No order was found with orderId: %s or cartId: %s", httpStatus = HttpStatus.NOT_FOUND)
    int NO_ORDER_FOUND = 3020;

    @ErrorConfig(group = Group.BUSINESS, message = "One cart type is not allowed to read an order of another cart type. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int CROSS_CARTTYPE_FORBIDDEN_READ = 3021;

    @ErrorConfig(group = Group.BUSINESS, message = "User is not authorized to access this order.", httpStatus = HttpStatus.UNAUTHORIZED)
    int UNAUTHORIZED_ORDER_ACCESS = 3022;

    @ErrorConfig(group = Group.BUSINESS, message = "Dining cannot be reserved.")
    int UNABLE_TO_BOOK_DINING = 3023;

    @ErrorConfig(group = Group.BUSINESS, message = "The transaction is not authorized by anti fraud service. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int AFS_AUTHORIZATION_FAILED = 3024;

    @ErrorConfig(group = Group.BUSINESS, message = "Payment pre-authorization failed from payments service. Please review payment information. [%s]",
            httpStatus = HttpStatus.BAD_REQUEST)
    int PAYMENT_AUTHORIZATION_FAILED = 3025;

    @ErrorConfig(group = Group.BACKEND, message = "Payments service is unable to process the transaction, possibly due to unexpected system issues. [%s]",
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int UNABLE_TO_VALIDATE_PAYMENT_METHOD = 3026;

    @ErrorConfig(group = Group.BUSINESS, message = "One cart version is not allowed to read an order of another cart version. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int CROSS_CARTVERSION_FORBIDDEN_READ = 3027;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to get reservation preview. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_RESERVATION_PREVIEW = 3028;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to find the room reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_ROOM_RESERVATION = 3029;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to find the show reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_SHOW_RESERVATION = 3030;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to update room reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_UPDATE_ROOM_RESERVATION = 3031;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to cancel room reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CANCEL_ROOM_RESERVATION = 3032;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to void payment. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_VOID_PAYMENT = 3033;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to capture payment. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CAPTURE_PAYMENT = 3034;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to refund payment. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_REFUND_PAYMENT = 3035;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to get payment session. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_PAYMENT_SESSION = 3036;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to create payment session. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CREATE_PAYMENT_SESSION = 3037;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to update payment session. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_UPDATE_PAYMENT_SESSION = 3038;

    @ErrorConfig(group = Group.BUSINESS, message = "Unable to find the dining reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_DINING_RESERVATION = 3039;

    @ErrorConfig(group = Group.BUSINESS, message = "No reservation was found with confirmationNumber.", httpStatus = HttpStatus.NOT_FOUND)
    int NO_RESERVATION_FOUND = 3040;
    
    @ErrorConfig(group = Group.BUSINESS, message = "Cart does not have a valid payment session id. cartId: [%s]", httpStatus = HttpStatus.NOT_FOUND)
    int CART_PAYMENT_SESSION_ID_NOT_FOUND = 3041;
    
    @ErrorConfig(group = Group.BACKEND, message = "Unexpected exception occurred during get reservation processing: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int UNEXPECTED_EXCEPTION_DURING_GET_RESERVATION = 3042;
    
    @ErrorConfig(group = Group.BACKEND, message = "Unexpected exception occurred during reservation preview processing: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int UNEXPECTED_EXCEPTION_DURING_RESERVATION_PREVIEW = 3043;

    @ErrorConfig(group = Group.BACKEND, message = "Unexpected exception occurred during reservation cancellation processing: %s", httpStatus = HttpStatus.INTERNAL_SERVER_ERROR)
    int UNEXPECTED_EXCEPTION_DURING_RESERVATION_CANCEL = 3044;
    
    @ErrorConfig(group = Group.BUSINESS, message = "Unable to commit refund for room reservation. [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_COMMIT_REFUND_ROOM_RESERVATION = 3045;
    
    @ErrorConfig(group = Group.BUSINESS, message = "Unable to release room reservation with confirmation number [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_RELEASE_ROOM_RESERVATION = 3046;

    @ErrorConfig(group = Group.BACKEND, message = "Unable to get customer profile: [%s]", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_GET_CUSTOMER_PROFILE = 3047;

    @ErrorConfig(group = Group.BACKEND, message = "Invalid gse id. Could not create itinerary for customer id|mlife : %s", httpStatus = HttpStatus.BAD_REQUEST)
    int UNABLE_TO_CREATE_ITINERARY_INVALID_GSE_ID = 3048;

    @ErrorConfig(group = Group.BACKEND, message = "The encrypted email is not matching with the retrieved order : %s", httpStatus = HttpStatus.BAD_REQUEST)
    int GET_ORDER_EMAIL_MISMATCH = 3049;
}
