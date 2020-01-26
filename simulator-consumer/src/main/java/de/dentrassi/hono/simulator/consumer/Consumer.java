/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.simulator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import io.micrometer.core.instrument.Counter;
import java.util.Date;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Consumer {

    private final Counter messages;
    private final Counter payload;
    private JsonObject bucket;
    private LinkedList<Double> queue;
    private HashMap<String, Object> pollQueue;
    double a = 1;

    public Consumer(final Counter messages, final Counter payload, JsonObject bucket) {
        this.messages = messages;
        this.payload = payload;
        this.bucket = bucket;
        pollQueue = new HashMap<String, Object>();
    }

    public void handleMessage(final Message msg) {
        this.messages.increment();
        Object k = msg.getMessageAnnotations().getValue().get(Symbol.valueOf("resource"));
        Object t = msg.getMessageAnnotations().getValue().get(Symbol.valueOf("tenant_id"));
        Object d = msg.getMessageAnnotations().getValue().get(Symbol.valueOf("device_id"));
        String type = ((String)k).contains("/")?((String) k).split("/")[0]:"unknown";
        System.out.println(msg.getMessageAnnotations().getValue().get(Symbol.valueOf("tenant_id")) + " " +
                msg.getMessageAnnotations().getValue().get(Symbol.valueOf("device_id")) + " " +
                msg.getMessageAnnotations().getValue().get(Symbol.valueOf("resource")) + " " +
                msg.getBody().toString());
        HashMap<String, Object> resource;
        LinkedList<Object> queue;
        if (pollQueue.get(k) != null)
            resource = (HashMap<String, Object>)pollQueue.get(k);
        else{
            resource = new HashMap<String, Object>();
            resource.put("tenant",(String)t);
            resource.put("device",(String)d);
            resource.put("type",type);
            pollQueue.put((String) k,(Object)resource);
        }
        resource.put("status","online");
        resource.put("lasttimestamp", msg.getCreationTime());
        if (resource.get("data") != null)
            queue = (LinkedList<Object>) resource.get("data");
        else{
            queue = new LinkedList<Object>();
            resource.put("data", queue);
        }
        double value = 0;
        try{
            JsonObject pay = new JsonObject(Buffer.buffer((((Data)msg.getBody()).getValue().getArray())));
            //support for KuraPayload {"sentOn":,"metrics":{"errorCode":0,"type":"value":}}
            if(pay.containsKey("metrics"))pay=pay.getJsonObject("metrics");
            //standard payload {"value":}
            value = pay.getDouble("value");
        }catch (Exception e){
            System.out.println("Parsing error: "+msg.getBody()+" - "+e);
        }
        JsonObject stat = new JsonObject();
        stat.put("timestamp",msg.getCreationTime());
        stat.put("value",value);
        queue.add(stat);
        if(queue.size()>20){
            queue.remove();
        }
//        JsonArray jArray = bucket.getJsonArray("data");
//        for(int i=0; i < jArray.size(); i++)
//            System.out.println(jArray.getDouble(i));
        JsonObject payload = new JsonObject(pollQueue);
        bucket.put("data", payload);
        final Section body = msg.getBody();
        if (body instanceof Data) {
            final Binary valueBuf = ((Data) body).getValue();
            if (valueBuf != null) {
                this.payload.increment(valueBuf.getLength());
            }
        }
    }

    public double count() {
        return messages.count();
    }
}
