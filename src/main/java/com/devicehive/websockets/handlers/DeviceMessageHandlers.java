package com.devicehive.websockets.handlers;


import com.devicehive.dao.DeviceCommandDAO;
import com.devicehive.dao.DeviceDAO;
import com.devicehive.exceptions.HiveWebsocketException;
import com.devicehive.model.*;
import com.devicehive.service.DeviceService;
import com.devicehive.websockets.handlers.annotations.Action;
import com.devicehive.websockets.json.GsonFactory;
import com.devicehive.websockets.json.strategies.*;
import com.devicehive.websockets.messagebus.global.MessagePublisher;
import com.devicehive.websockets.messagebus.local.LocalMessageBus;
import com.devicehive.websockets.util.WebsocketSession;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jms.JMSException;
import javax.transaction.Transactional;
import javax.websocket.Session;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DeviceMessageHandlers implements HiveMessageHandlers {

    private static final Logger logger = LoggerFactory.getLogger(DeviceMessageHandlers.class);

    @Inject
    private LocalMessageBus localMessageBus;

    @Inject
    private MessagePublisher messagePublisher;

    @Inject
    private DeviceDAO deviceDAO;


    @Inject
    private DeviceCommandDAO deviceCommandDAO;


    @Inject
    private DeviceService deviceService;

    @Action(value = "authenticate", needsAuth = false)
    public JsonObject processAuthenticate(JsonObject message, Session session) {
        UUID deviceId = GsonFactory.createGson().fromJson(message.get("deviceId"), UUID.class);
        String deviceKey = message.get("deviceKey").getAsString();

        Device device = deviceDAO.findByUUIDAndKey(deviceId, deviceKey);

        if (device != null) {
            WebsocketSession.setAuthorisedDevice(session, device);
            return JsonMessageBuilder.createSuccessResponseBuilder().build();
        } else {
            throw new HiveWebsocketException("Device authentication error: credentials are incorrect");
        }
    }

    @Override
    public void ensureAuthorised(JsonObject request, Session session) {
        Gson gson = GsonFactory.createGson();

        if (WebsocketSession.hasAuthorisedDevice(session)) {
            return;
        }
        UUID deviceId = gson.fromJson(request.get("deviceId"), UUID.class);
        String deviceKey = request.get("deviceKey").getAsString();

        Device device = deviceDAO.findByUUIDAndKey(deviceId, deviceKey);
        if (device == null) {
            throw new HiveWebsocketException("Not authorised");
        }
    }

    @Action(value = "command/update")
    public JsonObject processCommandUpdate(JsonObject message, Session session) throws JMSException {
        DeviceCommand command = deviceCommandDAO.findById(message.get("commandId").getAsLong());
        DeviceCommand update = GsonFactory.createGson(new CommandUpdateExclusionStrategy())
            .fromJson(message.getAsJsonObject("command"), DeviceCommand.class);

        Device device = WebsocketSession.getAuthorisedDevice(session);
        if (device == null) {
            device = deviceDAO.findByUUID(GsonFactory.createGson().fromJson(message.getAsJsonPrimitive("deviceId"), UUID.class));
        }

        deviceService.submitDeviceCommandUpdate(update, device);

        return JsonMessageBuilder.createSuccessResponseBuilder().build();
    }

    @Action(value = "command/subscribe")
    public JsonObject processNotificationSubscribe(JsonObject message, Session session) {
        Gson gson = GsonFactory.createGson();
        Date timestamp = gson.fromJson(message.getAsJsonPrimitive("timestamp"), Date.class);
        timestamp = timestamp != null ? timestamp : new Date();
        UUID deviceId = gson.fromJson(message.getAsJsonPrimitive("deviceId"), UUID.class);

        synchronized (session) {
            localMessageBus.subscribeToCommands(deviceId, session);
            List<DeviceCommand> oldCommands = new ArrayList<DeviceCommand>();//TODO get non-delivered commands from DB
            for (DeviceCommand dc : oldCommands) {
                //TODO create json

                //TODO mark dc as delivered
            }
        }
        return JsonMessageBuilder.createSuccessResponseBuilder().build();
    }

    @Action(value = "command/unsubscribe")
    public JsonObject processNotificationUnsubscribe(JsonObject message, Session session) {
        UUID deviceId = GsonFactory.createGson().fromJson(message.getAsJsonPrimitive("deviceId"), UUID.class);
        localMessageBus.unsubscribeFromCommands(deviceId, session);
        return JsonMessageBuilder.createSuccessResponseBuilder().build();
    }

    @Action(value = "notification/insert")
    public JsonObject processNotificationInsert(JsonObject message, Session session) throws JMSException {

        DeviceNotification deviceNotification = GsonFactory.createGson(new NotificationInsertRequestExclusionStrategy())
                .fromJson(message.get("notification"), DeviceNotification.class);


        // TODO do we need the same logic somewhere else?
        Device device = getDevice(session, message);

        deviceService.submitDeviceNotification(deviceNotification, device);

        JsonObject jsonObject = JsonMessageBuilder.createSuccessResponseBuilder()
            .addElement("notification", new JsonObject())
            .build();
        return jsonObject;
    }

    @Action(value = "server/info")
    @Transactional
    public JsonObject processServerInfo(JsonObject message, Session session) {
        Gson gson = GsonFactory.createGson(new ServerInfoExclusionStrategy());
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setApiVersion(Version.VERSION);
        apiInfo.setServerTimestamp(new Date());
        apiInfo.setWebSocketServerUrl("TODO_URL");
        JsonObject jsonObject = JsonMessageBuilder.createSuccessResponseBuilder()
            .addElement("info", gson.toJsonTree(apiInfo))
            .build();
        return jsonObject;
    }

    @Action(value = "device/get")
    public JsonObject processDeviceGet(JsonObject message, Session session) {
        Gson gson =  GsonFactory.createGson();
        JsonElement requestId = message.get("requestId");
        UUID deviceId = GsonFactory.createGson().fromJson(message.get("deviceId"),
                UUID.class);
        Device device = deviceDAO.findByUUID(deviceId);

        Gson gsonResponse = GsonFactory.createGson(new DeviceGetExclusionStrategy());
        JsonElement deviceElem = gsonResponse.toJsonTree(device);
        JsonObject result = JsonMessageBuilder.createSuccessResponseBuilder()
                .addAction("\"device/get\"")
                .addRequestId(requestId)
                .addElement("device", deviceElem)
                .build();
        return result;
    }

    @Action(value = "device/save", needsAuth = false)
    public JsonObject processDeviceSave(JsonObject message, Session session) {
        UUID deviceId = GsonFactory.createGson(new DeviceSaveExclusionStrategy()).fromJson(message.get("deviceId"), UUID.class);
        String deviceKey = message.get("deviceKey").getAsString();

        Device device = GsonFactory.createGson().fromJson(message.get("device"), Device.class);

        //TODO
        JsonObject jsonObject = JsonMessageBuilder.createSuccessResponseBuilder().build();
        return jsonObject;
    }


    private Device getDevice(Session session, JsonObject request) {
        Device device = WebsocketSession.getAuthorisedDevice(session);
        if (device == null) {
            device = deviceDAO.findByUUID(GsonFactory.createGson().fromJson(request.getAsJsonPrimitive("deviceId"), UUID.class));
        }
        return device;
    }

}
