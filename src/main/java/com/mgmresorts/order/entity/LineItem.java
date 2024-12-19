package com.mgmresorts.order.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mgmresorts.order.dto.AddOnComponent;
import com.mgmresorts.order.dto.DeliveryMethod;
import com.mgmresorts.order.dto.services.Message;
import com.mgmresorts.shopping.cart.dto.AgentInfo;

@JsonPropertyOrder({ "id", "cartLineItemId", "confirmationNumber", "status", "messages", "lineItemCharge", "lineItemTax", "lineItemPrice", "lineItemDeposit", "lineItemDiscount",
        "lineItemBalance", "specialRequests", "addOnComponents", "lineItemTourismFeeAndTax", "lineItemResortFeePerNight", "lineItemOccupancyFee", "lineItemResortFeeAndTax",
        "lineItemAdjustedItemSubtotal", "lineItemServiceChargeFeeAndTax", "lineItemTripSubtotal", "enableJwb", "f1Package", "numberOfNights", "selectedDeliveryMethod",
        "lineItemDeliveryMethodFee", "lineItemLet", "lineItemGratuity", "lineItemServiceChargeFee", "lineItemServiceChargeTax", "lineItemTransactionFee",
        "lineItemTransactionFeeTax", "lineItemCasinoSurcharge", "lineItemCasinoSurchargeAndTax", "numberOfTickets", "productType", "productId", "propertyId",
        "programId", "packageId", "packageLineItemId", "addOnsPrice", "addOnsTax", "averagePricePerNight", "content", "contentVersion", "lineItemStrikethroughPrice",
        "operaConfirmationNumber", "operaHotelCode", "itineraryId", "agentInfo", "reservationDate", "reservationTime", "upgraded" })
public class LineItem {

    @JsonProperty("id")
    private String id;

    @JsonProperty("cartLineItemId")
    private String cartLineItemId;

    @JsonProperty("confirmationNumber")
    private String confirmationNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("messages")
    private List<Message> messages = new ArrayList<Message>();

    @JsonProperty("lineItemCharge")
    private Double lineItemCharge;

    @JsonProperty("lineItemTax")
    private Double lineItemTax;

    @JsonProperty("lineItemPrice")
    private Double lineItemPrice;
    
    @JsonProperty("lineItemStrikethroughPrice")
    private Double lineItemStrikethroughPrice;

    @JsonProperty("lineItemDeposit")
    private Double lineItemDeposit;

    @JsonProperty("lineItemDiscount")
    private Double lineItemDiscount;

    @JsonProperty("lineItemBalance")
    private Double lineItemBalance;

    @JsonProperty("lineItemTourismFeeAndTax")
    private Double lineItemTourismFeeAndTax;

    @JsonProperty("lineItemResortFeePerNight")
    private Double lineItemResortFeePerNight;

    @JsonProperty("lineItemOccupancyFee")
    private Double lineItemOccupancyFee;

    @JsonProperty("lineItemResortFeeAndTax")
    private Double lineItemResortFeeAndTax;

    @JsonProperty("lineItemAdjustedItemSubtotal")
    private Double lineItemAdjustedItemSubtotal;

    @JsonProperty("lineItemServiceChargeFeeAndTax")
    private Double lineItemServiceChargeFeeAndTax;

    @JsonProperty("lineItemTripSubtotal")
    private Double lineItemTripSubtotal;

    @JsonProperty("specialRequests")
    private List<String> specialRequests = new ArrayList<String>();

    @JsonProperty("addOnComponents")
    private List<AddOnComponent> addOnComponents = new ArrayList<AddOnComponent>();

    @JsonProperty("enableJwb")
    private boolean enableJwb;

    @JsonProperty("f1Package")
    private boolean f1Package;

    @JsonProperty("numberOfNights")
    private Integer numberOfNights;

    @JsonProperty("selectedDeliveryMethod")
    private DeliveryMethod selectedDeliveryMethod;

    @JsonProperty("lineItemDeliveryMethodFee")
    private Double lineItemDeliveryMethodFee;

    @JsonProperty("lineItemLet")
    private Double lineItemLet;

    @JsonProperty("lineItemGratuity")
    private Double lineItemGratuity;

    @JsonProperty("lineItemServiceChargeFee")
    private Double lineItemServiceChargeFee;

    @JsonProperty("lineItemServiceChargeTax")
    private Double lineItemServiceChargeTax;

    @JsonProperty("lineItemTransactionFee")
    private Double lineItemTransactionFee;

    @JsonProperty("lineItemTransactionFeeTax")
    private Double lineItemTransactionFeeTax;

    @JsonProperty("lineItemCasinoSurcharge")
    private Double lineItemCasinoSurcharge;

    @JsonProperty("lineItemCasinoSurchargeAndTax")
    private Double lineItemCasinoSurchargeAndTax;

    @JsonProperty("numberOfTickets")
    private Integer numberOfTickets;

    @JsonProperty("productType")
    private ProductType productType;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("propertyId")
    private String propertyId;

    @JsonProperty("programId")
    private String programId;

    @JsonProperty("packageId")
    private String packageId;

    @JsonProperty("packageLineItemId")
    private String packageLineItemId;

    @JsonProperty("addOnsPrice")
    private Double addOnsPrice;

    @JsonProperty("addOnsTax")
    private Double addOnsTax;

    @JsonProperty("averagePricePerNight")
    private Double averagePricePerNight;

    @JsonProperty("content")
    private String content;

    @JsonProperty("contentVersion")
    private String contentVersion;

    @JsonProperty("operaConfirmationNumber")
    private String operaConfirmationNumber;

    @JsonProperty("operaHotelCode")
    private String operaHotelCode;
    
    @JsonProperty("itineraryId")
    private String itineraryId;

    @JsonProperty("reservationDate")
    private String reservationDate;

    @JsonProperty("reservationTime")
    private String reservationTime;

    @JsonProperty("upgraded")
    private boolean upgraded;

    @JsonProperty("agentInfo")
    private com.mgmresorts.shopping.cart.dto.AgentInfo agentInfo;

    public boolean getEnableJwb() {
        return enableJwb;
    }

    public void setEnableJwb(boolean enableJwb) {
        this.enableJwb = enableJwb;
    }

    public boolean getF1Package() {
        return f1Package;
    }

    public void setF1Package(boolean f1Package) {
        this.f1Package = f1Package;
    }

    public String getId() {
        return id;
    }

    public void setId(String lineItemId) {
        this.id = lineItemId;
    }

    public String getCartLineItemId() {
        return cartLineItemId;
    }

    public void setCartLineItemId(String cartLineItemId) {
        this.cartLineItemId = cartLineItemId;
    }

    public String getConfirmationNumber() {
        return confirmationNumber;
    }

    public void setConfirmationNumber(String confirmationNumber) {
        this.confirmationNumber = confirmationNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Double getLineItemCharge() {
        return lineItemCharge;
    }

    public void setLineItemCharge(Double lineItemCharge) {
        this.lineItemCharge = lineItemCharge;
    }

    public Double getLineItemTax() {
        return lineItemTax;
    }

    public void setLineItemTax(Double lineItemTax) {
        this.lineItemTax = lineItemTax;
    }

    public Double getLineItemPrice() {
        return lineItemPrice;
    }

    public void setLineItemPrice(Double lineItemPrice) {
        this.lineItemPrice = lineItemPrice;
    }

    public Double getLineItemStrikethroughPrice() {
        return lineItemStrikethroughPrice;
    }

    public void setLineItemStrikethroughPrice(Double lineItemStrikethroughPrice) {
        this.lineItemStrikethroughPrice = lineItemStrikethroughPrice;
    }

    public Double getLineItemDeposit() {
        return lineItemDeposit;
    }

    public void setLineItemDeposit(Double lineItemDeposit) {
        this.lineItemDeposit = lineItemDeposit;
    }

    public Double getLineItemDiscount() {
        return lineItemDiscount;
    }

    public void setLineItemDiscount(Double lineItemDiscount) {
        this.lineItemDiscount = lineItemDiscount;
    }

    public Double getLineItemBalance() {
        return lineItemBalance;
    }

    public void setLineItemBalance(Double lineItemBalance) {
        this.lineItemBalance = lineItemBalance;
    }

    public List<String> getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests(List<String> specialRequests) {
        this.specialRequests = specialRequests;
    }

    public List<AddOnComponent> getAddOnComponents() {
        return addOnComponents;
    }

    public void setAddOnComponents(List<AddOnComponent> addOnComponents) {
        this.addOnComponents = addOnComponents;
    }

    public Double getLineItemTourismFeeAndTax() {
        return lineItemTourismFeeAndTax;
    }

    public Double getLineItemResortFeePerNight() {
        return lineItemResortFeePerNight;
    }

    public Double getLineItemOccupancyFee() {
        return lineItemOccupancyFee;
    }

    public Double getLineItemResortFeeAndTax() {
        return lineItemResortFeeAndTax;
    }

    public void setLineItemTourismFeeAndTax(Double lineItemTourismFeeAndTax) {
        this.lineItemTourismFeeAndTax = lineItemTourismFeeAndTax;
    }

    public void setLineItemResortFeePerNight(Double lineItemResortFeePerNight) {
        this.lineItemResortFeePerNight = lineItemResortFeePerNight;
    }

    public void setLineItemOccupancyFee(Double lineItemOccupancyFee) {
        this.lineItemOccupancyFee = lineItemOccupancyFee;
    }

    public void setLineItemResortFeeAndTax(Double lineItemResortFeeAndTax) {
        this.lineItemResortFeeAndTax = lineItemResortFeeAndTax;
    }

    public Integer getNumberOfNights() {
        return numberOfNights;
    }

    public void setNumberOfNights(Integer numberOfNights) {
        this.numberOfNights = numberOfNights;
    }

    public Double getLineItemAdjustedItemSubtotal() {
        return lineItemAdjustedItemSubtotal;
    }

    public void setLineItemAdjustedItemSubtotal(Double lineItemAdjustedItemSubtotal) {
        this.lineItemAdjustedItemSubtotal = lineItemAdjustedItemSubtotal;
    }

    public Double getLineItemServiceChargeFeeAndTax() {
        return lineItemServiceChargeFeeAndTax;
    }

    public void setLineItemServiceChargeFeeAndTax(Double lineItemServiceChargeFeeAndTax) {
        this.lineItemServiceChargeFeeAndTax = lineItemServiceChargeFeeAndTax;
    }

    public Double getLineItemTripSubtotal() {
        return lineItemTripSubtotal;
    }

    public void setLineItemTripSubtotal(Double lineItemTripSubtotal) {
        this.lineItemTripSubtotal = lineItemTripSubtotal;
    }


    public DeliveryMethod getSelectedDeliveryMethod() {
        return selectedDeliveryMethod;
    }

    public void setSelectedDeliveryMethod(DeliveryMethod selectedDeliveryMethod) {
        this.selectedDeliveryMethod = selectedDeliveryMethod;
    }

    public Double getLineItemDeliveryMethodFee() {
        return lineItemDeliveryMethodFee;
    }

    public void setLineItemDeliveryMethodFee(Double lineItemDeliveryMethodFee) {
        this.lineItemDeliveryMethodFee = lineItemDeliveryMethodFee;
    }

    public Double getLineItemLet() {
        return lineItemLet;
    }

    public void setLineItemLet(Double lineItemLet) {
        this.lineItemLet = lineItemLet;
    }

    public Double getLineItemGratuity() {
        return lineItemGratuity;
    }

    public void setLineItemGratuity(Double lineItemGratuity) {
        this.lineItemGratuity = lineItemGratuity;
    }

    public Double getLineItemServiceChargeFee() {
        return lineItemServiceChargeFee;
    }

    public void setLineItemServiceChargeFee(Double lineItemServiceChargeFee) {
        this.lineItemServiceChargeFee = lineItemServiceChargeFee;
    }

    public Double getLineItemServiceChargeTax() {
        return lineItemServiceChargeTax;
    }

    public void setLineItemServiceChargeTax(Double lineItemServiceChargeTax) {
        this.lineItemServiceChargeTax = lineItemServiceChargeTax;
    }

    public Double getLineItemTransactionFee() {
        return lineItemTransactionFee;
    }

    public void setLineItemTransactionFee(Double lineItemTransactionFee) {
        this.lineItemTransactionFee = lineItemTransactionFee;
    }

    public Double getLineItemTransactionFeeTax() {
        return lineItemTransactionFeeTax;
    }

    public void setLineItemTransactionFeeTax(Double lineItemTransactionFeeTax) {
        this.lineItemTransactionFeeTax = lineItemTransactionFeeTax;
    }

    public Double getLineItemCasinoSurcharge() {
        return lineItemCasinoSurcharge;
    }

    public void setLineItemCasinoSurcharge(Double lineItemCasinoSurcharge) {
        this.lineItemCasinoSurcharge = lineItemCasinoSurcharge;
    }

    public Double getLineItemCasinoSurchargeAndTax() {
        return lineItemCasinoSurchargeAndTax;
    }

    public void setLineItemCasinoSurchargeAndTax(Double lineItemCasinoSurchargeAndTax) {
        this.lineItemCasinoSurchargeAndTax = lineItemCasinoSurchargeAndTax;
    }

    public Integer getNumberOfTickets() {
        return numberOfTickets;
    }

    public void setNumberOfTickets(Integer numberOfTickets) {
        this.numberOfTickets = numberOfTickets;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public String getProgramId() {
        return programId;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getPackageLineItemId() {
        return packageLineItemId;
    }

    public void setPackageLineItemId(String packageLineItemId) {
        this.packageLineItemId = packageLineItemId;
    }

    public Double getAddOnsPrice() {
        return addOnsPrice;
    }

    public Double getAddOnsTax() {
        return addOnsTax;
    }

    public Double getAveragePricePerNight() {
        return averagePricePerNight;
    }

    public String getContent() {
        return content;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public String getOperaConfirmationNumber() {
        return operaConfirmationNumber;
    }

    public String getOperaHotelCode() {
        return operaHotelCode;
    }

    public void setAddOnsPrice(Double addOnsPrice) {
        this.addOnsPrice = addOnsPrice;
    }

    public void setAddOnsTax(Double addOnsTax) {
        this.addOnsTax = addOnsTax;
    }

    public void setAveragePricePerNight(Double averagePricePerNight) {
        this.averagePricePerNight = averagePricePerNight;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public void setOperaConfirmationNumber(String operaConfirmationNumber) {
        this.operaConfirmationNumber = operaConfirmationNumber;
    }

    public void setOperaHotelCode(String operaHotelCode) {
        this.operaHotelCode = operaHotelCode;
    }

    public String getItineraryId() {
        return itineraryId;
    }

    public void setItineraryId(String itineraryId) {
        this.itineraryId = itineraryId;
    }

    public String getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(String reservationDate) {
        this.reservationDate = reservationDate;
    }

    public String getReservationTime() {
        return reservationTime;
    }

    public void setReservationTime(String reservationTime) {
        this.reservationTime = reservationTime;
    }

    public AgentInfo getAgentInfo() {
        return agentInfo;
    }

    public void setAgentInfo(AgentInfo agentInfo) {
        this.agentInfo = agentInfo;
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
    }
}
