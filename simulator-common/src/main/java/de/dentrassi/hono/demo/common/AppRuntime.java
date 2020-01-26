/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

public class AppRuntime implements AutoCloseable {

    private final Vertx vertx;
    private final HealthCheckHandler healthCheckHandler;
    private final MeterRegistry registry;
    JsonObject bucket = new JsonObject();


    public AppRuntime() {
        this(null);
    }

    public AppRuntime(final Consumer<VertxOptions> customizer) {

        final VertxOptions options = new VertxOptions();

        options.setPreferNativeTransport(true);
        options.setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setEnabled(true)
                        .setDisabledMetricsCategories(EnumSet.allOf(MetricsDomain.class))
                        .setPrometheusOptions(
                                new VertxPrometheusOptions()
                                        .setEnabled(true)));

        if (customizer != null) {
            customizer.accept(options);
        }

        this.vertx = Vertx.vertx(options);

        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");
        allowedHeaders.add("X-PINGARUNER");

        final Router router = Router.router(this.vertx);

        router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders));

        this.vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080);

        router.route("/metrics").handler(PrometheusScrapingHandler.create());

        this.healthCheckHandler = HealthCheckHandler.create(this.vertx);
        router.get("/health").handler(this.healthCheckHandler);

        router.route("/api/overview").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            if (bucket == null) {
                response.setStatusCode(404).end();
            } else {
                response.setStatusCode(200).putHeader("Content-Type", "application/json").end(bucket.encodePrettily());
            }
        });

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor();

        Runnable periodicTask = new Runnable() {
            public void run() {
                try {
                    if(bucket!=null) {
                        JsonObject a = bucket.copy();
                        a.getJsonObject("data").getMap().keySet().forEach((key) -> {
                            String status = retentionControl(a.getJsonObject("data").getJsonObject(key), key.contains("telemetry") ? "telemetry" : "event");
                            if (status != "false" && status != "true")
                                bucket.getJsonObject("data").getJsonObject(key).put("status", status);
                            else if (status == "false") bucket.getJsonObject("data").remove(key);
                        });
                    }
                } catch (Exception e) {

                }
            }
        };

        executor.scheduleAtFixedRate(periodicTask, 0, 1, TimeUnit.SECONDS);

        this.registry = BackendRegistries.getDefaultNow();
    }

    String retentionControl(JsonObject resource, String type){
        int retentionEvent = 3600;
        int warningTelemetry = 5;
        int retentionTelemetry = 10;
        int offlineDelay = 20;
        //check for old devices
        try {
            long now = System.currentTimeMillis()/1000;
            long timestamp = resource.getLong("lasttimestamp")/1000;
            if (type.equals("telemetry")) {
                System.out.println("Retention check for telemetry: "+ now + " - " + timestamp);
                if (timestamp > now - retentionTelemetry) {
                    if (timestamp < now - warningTelemetry)
                        return "warning";
                } else {
                    if(resource.getString("status").equals("disconnect"))
                        return "false";
                    else {
                        if (timestamp < now - warningTelemetry - offlineDelay)
                            return "disconnect";
                        return "offline";
                    }
                }
            } else if (type.equals("event")) {
                if (timestamp < now - retentionEvent)
                    return "false";
            }
        }catch (Exception e){
            System.out.println("Error handling old devices retention: "+e);
        }
        return "true";
    }

    @Override
    public void close() {
        vertx.close();
    }

    public void register(final String name, final Handler<Future<Status>> procedure) {
        this.healthCheckHandler.register(name, procedure);
    }

    public MeterRegistry getRegistry() {
        return this.registry;
    }

    public JsonObject getJsonBucket() {
        return this.bucket;
    }

    public Vertx getVertx() {
        return this.vertx;
    }
}
