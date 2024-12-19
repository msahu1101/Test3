package com.mgmresorts.order.event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.mgmresorts.common.crypto.SecurityFactory;
import com.mgmresorts.common.dto.services.WebHookResponse;
import com.mgmresorts.common.errors.SystemError;
import com.mgmresorts.common.exception.AppException;
import com.mgmresorts.common.logging.Logger;
import com.mgmresorts.common.utils.JSonMapper;
import com.mgmresorts.common.utils.Utils;
import com.mgmresorts.event.dto.Event;
import com.mgmresorts.event.dto.EventData;
import com.mgmresorts.event.dto.OrderServiceEvent;

public interface IEventHandler {
    JSonMapper mapper = new JSonMapper();
    Logger logger = Logger.get(IEventHandler.class);

    default <T> T getPayload(EventData data, Class<T> clazz) throws AppException {
        String payload = null;
        if (data.getPayload().getCallbackUrl() != null) {
            logger.error("Event payload is referenced {} ..", data.getPayload().getCallbackUrl());
            try {
                final InputStream stream = new URL(data.getPayload().getCallbackUrl()).openStream();
                final ReadableByteChannel channel = Channels.newChannel(stream);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final WritableByteChannel write = Channels.newChannel(out);
                final ByteBuffer bb = ByteBuffer.allocate(4096);
                while (channel.read(bb) != -1) {
                    bb.flip();
                    write.write(bb);
                    bb.clear();
                }
                channel.close();
                write.close();
                payload = out.toString();
            } catch (IOException e) {
                logger.error("Event payload invalid {} ", e.getMessage());
                throw new AppException(SystemError.INVALID_DATA, e);
            }
        } else {
            logger.error("Event payload is embeded ..");
            payload = data.getPayload().getContent();
        }

        final String privateKey = data.getPayload().getPrivateKey();
        if (!Utils.isEmpty(privateKey)) {
            payload = SecurityFactory.get().decrypt(payload, privateKey);
        }
        return mapper.readValue(payload, clazz);
    }

    void process(WebHookResponse response, Event event) throws AppException;
    
    void process(OrderServiceEvent event) throws AppException;
}
