/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import io.glutamate.lang.Environment;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public final class Payload {

    double payloadSize;
    private double counter;
    private JsonObject obj;
    static final String contentType = "application/octet-stream";

    public Payload() {
        payloadSize = Environment.getAs("PAYLOAD_SIZE", 20, Integer::parseInt);
        if(payloadSize <= 0) {
            payloadSize = 20;
        }
        obj = new JsonObject();
        //obj.put("name",deviceId); no need of this because the id is in the header
    }

    public void inc(){
        counter=(counter+1)%payloadSize;
        obj.put("value",counter);
    }

    public Buffer getBuffer() {
        inc();
        return obj.toBuffer();
    }

    static public String getContentType() {
        return contentType;
    }

}
