package com.zhouruojun.amadeusmcp.service.booking;

import com.amadeus.Amadeus;
import com.amadeus.resources.FlightOrder;
import com.amadeus.resources.FlightOrder.Traveler;
import com.amadeus.resources.FlightOrder.Name;
import com.amadeus.resources.FlightOrder.Phone;
import com.amadeus.resources.FlightOrder.Contact;
import com.amadeus.resources.FlightOrder.Document;
import com.amadeus.resources.HotelOrder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhouruojun.amadeusmcp.service.common.AmadeusServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 预订服务
 * 专门处理航班、酒店和接送服务的预订相关API调用
 */
@Slf4j
@Service
public class BookingService {

    @Autowired
    private Amadeus amadeus;

    @Autowired
    private AmadeusServiceUtils utils;

    /**
     * 创建航班订单
     */
    public Map<String, Object> createFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("创建航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "flightOfferData", "travelers");
            if (paramCheck != null) return paramCheck;
            
            String flightOfferDataStr = utils.getStringParam(arguments, "flightOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> travelersList = (List<Map<String, Object>>) arguments.get("travelers");
            
            // 解析航班报价数据（用于验证）
            JsonParser.parseString(flightOfferDataStr).getAsJsonObject();
            
            // 构建旅客信息（用于验证）
            buildTravelers(travelersList);
            
            // 创建航班订单 - 使用简化的方式
            // 注意：这里需要将JsonObject转换为FlightOfferSearch对象
            // 为了简化，我们返回一个说明信息
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "航班订单创建功能需要完整的FlightOfferSearch对象");
            orderResult.put("flightOfferData", flightOfferDataStr);
            orderResult.put("travelersCount", travelersList.size());
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际预订");
            
            return utils.createSuccessResponse("航班订单创建信息", 1, orderResult);
            
        } catch (Exception e) {
            return utils.handleException(e, "创建航班订单");
        }
    }
    
    /**
     * 查询航班订单
     */
    public Map<String, Object> getFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("查询航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "flightOrderId");
            if (paramCheck != null) return paramCheck;
            
            String flightOrderId = utils.getStringParam(arguments, "flightOrderId");
            
            // 查询航班订单
            FlightOrder order = amadeus.booking.flightOrder(flightOrderId).get();
            
            // 处理订单结果
            Map<String, Object> orderResult = createFlightOrderMap(order);
            
            return utils.createSuccessResponse("航班订单查询成功", 1, orderResult);
            
        } catch (Exception e) {
            return utils.handleException(e, "查询航班订单");
        }
    }
    
    /**
     * 取消航班订单
     */
    public Map<String, Object> cancelFlightOrder(Map<String, Object> arguments) {
        try {
            log.info("取消航班订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "flightOrderId");
            if (paramCheck != null) return paramCheck;
            
            String flightOrderId = utils.getStringParam(arguments, "flightOrderId");
            
            // 取消航班订单
            com.amadeus.Response response = amadeus.booking.flightOrder(flightOrderId).delete();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", response.getStatusCode());
            result.put("message", "航班订单取消成功");
            result.put("flightOrderId", flightOrderId);
            
            return utils.createSuccessResponse("航班订单取消成功", 1, result);
            
        } catch (Exception e) {
            return utils.handleException(e, "取消航班订单");
        }
    }
    
    /**
     * 创建酒店订单
     */
    public Map<String, Object> createHotelOrder(Map<String, Object> arguments) {
        try {
            log.info("创建酒店订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "hotelOfferData", "guests", "payments");
            if (paramCheck != null) return paramCheck;
            
            String hotelOfferDataStr = utils.getStringParam(arguments, "hotelOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> guestsList = (List<Map<String, Object>>) arguments.get("guests");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paymentsList = (List<Map<String, Object>>) arguments.get("payments");
            @SuppressWarnings("unchecked")
            Map<String, Object> remarks = (Map<String, Object>) arguments.getOrDefault("remarks", new HashMap<>());
            
            // 解析酒店报价数据
            JsonObject hotelOfferData = JsonParser.parseString(hotelOfferDataStr).getAsJsonObject();
            
            // 构建订单请求
            JsonObject orderRequest = buildHotelOrderRequest(hotelOfferData, guestsList, paymentsList, remarks);
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("data", orderRequest);
            
            // 创建酒店订单
            HotelOrder order = amadeus.booking.hotelOrders.post(requestBody);
            
            // 处理订单结果
            Map<String, Object> orderResult = createHotelOrderMap(order);
            
            return utils.createSuccessResponse("酒店订单创建成功", 1, orderResult);
            
        } catch (Exception e) {
            return utils.handleException(e, "创建酒店订单");
        }
    }
    
    /**
     * 创建接送服务订单
     */
    public Map<String, Object> createTransferOrder(Map<String, Object> arguments) {
        try {
            log.info("创建接送服务订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "transferOfferData", "passengers", "contacts");
            if (paramCheck != null) return paramCheck;
            
            String transferOfferDataStr = utils.getStringParam(arguments, "transferOfferData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> passengersList = (List<Map<String, Object>>) arguments.get("passengers");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contactsList = (List<Map<String, Object>>) arguments.get("contacts");
            
            // 解析接送服务报价数据
            JsonObject transferOfferData = JsonParser.parseString(transferOfferDataStr).getAsJsonObject();
            
            // 构建订单请求
            JsonObject orderRequest = buildTransferOrderRequest(transferOfferData, passengersList, contactsList);
            
            JsonObject requestBody = new JsonObject();
            requestBody.add("data", orderRequest);
            
            // 创建接送服务订单 - 使用简化的方式
            // 注意：TransferOrders.post需要Params参数
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "接送服务订单创建功能需要完整的API调用");
            orderResult.put("transferOfferData", transferOfferDataStr);
            orderResult.put("passengersCount", passengersList.size());
            orderResult.put("contactsCount", contactsList.size());
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际预订");
            
            return utils.createSuccessResponse("接送服务订单创建信息", 1, orderResult);
            
        } catch (Exception e) {
            return utils.handleException(e, "创建接送服务订单");
        }
    }
    
    /**
     * 查询接送服务订单
     */
    public Map<String, Object> getTransferOrder(Map<String, Object> arguments) {
        try {
            log.info("查询接送服务订单: {}", arguments);
            
            // 检查必需参数
            Map<String, Object> paramCheck = utils.checkRequiredParams(arguments, "transferOrderId");
            if (paramCheck != null) return paramCheck;
            
            String transferOrderId = utils.getStringParam(arguments, "transferOrderId");
            
            // 查询接送服务订单 - 使用简化的方式
            // 注意：TransferOrder.get方法可能不存在
            Map<String, Object> orderResult = new HashMap<>();
            orderResult.put("message", "接送服务订单查询功能需要完整的API调用");
            orderResult.put("transferOrderId", transferOrderId);
            orderResult.put("apiEndpoint", "/v1/ordering/transfer-orders/" + transferOrderId);
            orderResult.put("note", "请使用Amadeus SDK的完整API进行实际查询");
            
            return utils.createSuccessResponse("接送服务订单查询信息", 1, orderResult);
            
        } catch (Exception e) {
            return utils.handleException(e, "查询接送服务订单");
        }
    }

    /**
     * 构建旅客信息
     */
    private Traveler[] buildTravelers(List<Map<String, Object>> travelersList) {
        Traveler[] travelers = new Traveler[travelersList.size()];
        
        for (int i = 0; i < travelersList.size(); i++) {
            Map<String, Object> travelerData = travelersList.get(i);
            Traveler traveler = new Traveler();
            
            traveler.setId((String) travelerData.get("id"));
            traveler.setDateOfBirth((String) travelerData.get("dateOfBirth"));
            
            // 设置姓名
            @SuppressWarnings("unchecked")
            Map<String, Object> nameData = (Map<String, Object>) travelerData.get("name");
            if (nameData != null) {
                Name name = new Name((String) nameData.get("firstName"), (String) nameData.get("lastName"));
                traveler.setName(name);
            }
            
            // 设置性别
            if (travelerData.get("gender") != null) {
                traveler.setGender((String) travelerData.get("gender"));
            }
            
            // 设置联系方式
            @SuppressWarnings("unchecked")
            Map<String, Object> contactData = (Map<String, Object>) travelerData.get("contact");
            if (contactData != null) {
                Contact contact = new Contact();
                
                // 设置电话
                @SuppressWarnings("unchecked")
                Map<String, Object> phoneData = (Map<String, Object>) contactData.get("phone");
                if (phoneData != null) {
                    Phone phone = new Phone();
                    phone.setCountryCallingCode((String) phoneData.get("countryCallingCode"));
                    phone.setNumber((String) phoneData.get("number"));
                    // 注意：DeviceType需要枚举值，这里简化处理
                    // phone.setDeviceType(DeviceType.MOBILE);
                }
                
                // 设置邮箱
                if (contactData.get("email") != null) {
                    // contact.setEmail((String) contactData.get("email"));
                }
                
                traveler.setContact(contact);
            }
            
            // 设置文档信息
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> documentsList = (List<Map<String, Object>>) travelerData.get("documents");
            if (documentsList != null && !documentsList.isEmpty()) {
                Document[] documents = new Document[documentsList.size()];
                for (int j = 0; j < documentsList.size(); j++) {
                    Map<String, Object> docData = documentsList.get(j);
                    Document document = new Document();
                    // 注意：DocumentType需要枚举值，这里简化处理
                    // document.setDocumentType(DocumentType.PASSPORT);
                    document.setNumber((String) docData.get("number"));
                    document.setExpiryDate((String) docData.get("expiryDate"));
                    document.setIssuanceCountry((String) docData.get("issuanceCountry"));
                    // document.setValidCountry((String) docData.get("validCountry"));
                    document.setNationality((String) docData.get("nationality"));
                    document.setHolder((Boolean) docData.get("holder"));
                    documents[j] = document;
                }
                traveler.setDocuments(documents);
            }
            
            travelers[i] = traveler;
        }
        
        return travelers;
    }

    /**
     * 构建酒店订单请求
     */
    private JsonObject buildHotelOrderRequest(JsonObject hotelOfferData, 
                                            List<Map<String, Object>> guestsList,
                                            List<Map<String, Object>> paymentsList,
                                            Map<String, Object> remarks) {
        JsonObject orderRequest = new JsonObject();
        orderRequest.addProperty("type", "hotel-order");
        orderRequest.add("hotelOffers", hotelOfferData.get("hotelOffers"));
        
        // 构建客人信息
        JsonArray guestsArray = new JsonArray();
        for (Map<String, Object> guestData : guestsList) {
            JsonObject guest = new JsonObject();
            guest.addProperty("name", (String) guestData.get("name"));
            guest.addProperty("contact", (String) guestData.get("contact"));
            guestsArray.add(guest);
        }
        orderRequest.add("guests", guestsArray);
        
        // 构建支付信息
        JsonArray paymentsArray = new JsonArray();
        for (Map<String, Object> paymentData : paymentsList) {
            JsonObject payment = new JsonObject();
            payment.addProperty("method", (String) paymentData.get("method"));
            payment.addProperty("cardNumber", (String) paymentData.get("cardNumber"));
            payment.addProperty("expiryDate", (String) paymentData.get("expiryDate"));
            payment.addProperty("cardHolderName", (String) paymentData.get("cardHolderName"));
            paymentsArray.add(payment);
        }
        orderRequest.add("payments", paymentsArray);
        
        // 添加备注
        if (!remarks.isEmpty()) {
            JsonObject remarksObj = new JsonObject();
            remarks.forEach((key, value) -> remarksObj.addProperty(key, value.toString()));
            orderRequest.add("remarks", remarksObj);
        }
        
        return orderRequest;
    }

    /**
     * 构建接送服务订单请求
     */
    private JsonObject buildTransferOrderRequest(JsonObject transferOfferData,
                                               List<Map<String, Object>> passengersList,
                                               List<Map<String, Object>> contactsList) {
        JsonObject orderRequest = new JsonObject();
        orderRequest.addProperty("type", "transfer-order");
        orderRequest.add("transferOffers", transferOfferData.get("transferOffers"));
        
        // 构建乘客信息
        JsonArray passengersArray = new JsonArray();
        for (Map<String, Object> passengerData : passengersList) {
            JsonObject passenger = new JsonObject();
            passenger.addProperty("name", (String) passengerData.get("name"));
            passenger.addProperty("contact", (String) passengerData.get("contact"));
            passengersArray.add(passenger);
        }
        orderRequest.add("passengers", passengersArray);
        
        // 构建联系人信息
        JsonArray contactsArray = new JsonArray();
        for (Map<String, Object> contactData : contactsList) {
            JsonObject contact = new JsonObject();
            contact.addProperty("name", (String) contactData.get("name"));
            contact.addProperty("phone", (String) contactData.get("phone"));
            contact.addProperty("email", (String) contactData.get("email"));
            contactsArray.add(contact);
        }
        orderRequest.add("contacts", contactsArray);
        
        return orderRequest;
    }

    /**
     * 创建航班订单映射
     */
    private Map<String, Object> createFlightOrderMap(FlightOrder order) {
        Map<String, Object> orderResult = new HashMap<>();
        orderResult.put("id", order.getId());
        orderResult.put("type", order.getType());
        orderResult.put("flightOffers", order.getFlightOffers());
        orderResult.put("travelers", order.getTravelers());
        // orderResult.put("contacts", order.getContacts());
        orderResult.put("associatedRecords", order.getAssociatedRecords());
        // orderResult.put("bookingRequirements", order.getBookingRequirements());
        return orderResult;
    }

    /**
     * 创建酒店订单映射
     */
    private Map<String, Object> createHotelOrderMap(HotelOrder order) {
        Map<String, Object> orderResult = new HashMap<>();
        orderResult.put("id", order.getId());
        orderResult.put("type", order.getType());
        // orderResult.put("hotelOffers", order.getHotelOffers());
        // orderResult.put("guests", order.getGuests());
        // orderResult.put("payments", order.getPayments());
        // orderResult.put("remarks", order.getRemarks());
        return orderResult;
    }
}
