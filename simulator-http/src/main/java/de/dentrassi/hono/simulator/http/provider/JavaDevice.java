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
package de.dentrassi.hono.simulator.http.provider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import io.glutamate.lang.ThrowingConsumer;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class JavaDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("JAVA", JavaDevice::new);
        }

    }

    private final byte[] payload;

    public JavaDevice(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);
        this.payload = "{foo:42}".getBytes(StandardCharsets.UTF_8);
    }

    protected void process(final Statistics statistics, final HttpUrl url) throws IOException {

        final HttpURLConnection con = (HttpURLConnection) url.url().openConnection();
        try {
            con.setDoInput(false);
            con.setDoOutput(true);

            con.setConnectTimeout(1_000);
            con.setReadTimeout(1_000);
            con.setRequestMethod(this.method);
            con.setRequestProperty("Content-Type", JSON.toString());

            if (!NOAUTH) {
                con.setRequestProperty("Authorization", this.auth);
            }

            con.connect();

            try (final OutputStream out = con.getOutputStream()) {
                out.write(this.payload);
            }

            final int code = con.getResponseCode();
            if (code < 200 || code > 299) {
                handleFailure(code, statistics);
            } else {
                handleSuccess(statistics);
            }

        } finally {
            con.disconnect();
        }
    }

    @Override
    protected ThrowingConsumer<Statistics> tickTelemetryProvider() {
        final HttpUrl url = createUrl("telemetry");
        return s -> process(s, url);
    }

    @Override
    protected ThrowingConsumer<Statistics> tickEventProvider() {
        final HttpUrl url = createUrl("event");
        return s -> process(s, url);
    }

}