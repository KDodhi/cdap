/*
 * Copyright 2014 Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.continuuity.examples.helloworld;

import com.continuuity.api.annotation.Handle;
import com.continuuity.api.app.AbstractApplication;
import com.continuuity.api.procedure.AbstractProcedure;
import com.continuuity.api.procedure.ProcedureContext;
import com.continuuity.api.procedure.ProcedureRequest;
import com.continuuity.api.procedure.ProcedureResponder;
import com.continuuity.api.procedure.ProcedureResponse;
import com.continuuity.api.service.http.HttpServiceContext;
import com.continuuity.api.service.http.HttpServiceHandler;
import com.continuuity.api.service.http.HttpServiceRequest;
import com.continuuity.api.service.http.HttpServiceResponder;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import org.apache.twill.discovery.Discoverable;
import org.apache.twill.discovery.ServiceDiscovered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 *
 */
public class HelloWorld extends AbstractApplication {
  /**
   * Override this method to configure the application.
   */
  @Override
  public void configure() {
    setName("HttpServiceApp");
    setDescription("Application with Http Service");
    addService("HttpService", ImmutableList.<HttpServiceHandler>of(new BaseHttpHandler()));
    addProcedure(new NoOpProcedure());
  }

  /**
   *
   */
  @Path("/v1")
  public static final class BaseHttpHandler implements HttpServiceHandler {
    @GET
    @Path("/handle")
    public void process(HttpServiceRequest request, HttpServiceResponder responder) {
      System.err.println("GOT TO THE HANDLER METHOD!");
      responder.sendString("Hello World");
    }

    /**
     *
     * @param context
     * @throws Exception
     */
    @Override
    public void initialize(HttpServiceContext context) throws Exception {

    }

    /**
     *
     */
    @Override
    public void destroy() {

    }
  }

  /**
   *
   */
  public static final class NoOpProcedure extends AbstractProcedure {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpProcedure.class);

    private ServiceDiscovered serviceDiscovered;

    /**
     *
     * @param context
     */
    @Override
    public void initialize(ProcedureContext context) {
      //Discover the UserInterestsLookup service via discovery service
      LOG.debug("GOT INITIALIZE");
      serviceDiscovered = context.discover("HttpServiceApp", "HttpService", "HttpService");
      LOG.debug("GOT AFTER INITIALIZE");
      LOG.debug("SERVICE NAME: {}, SERVICE DISCOVERABLE: {}", serviceDiscovered.getName(),
                serviceDiscovered.iterator().hasNext());
    }

    /**
     *
     * @param request
     * @param responder
     * @throws Exception
     */
    @Handle("noop")
    public void handle(ProcedureRequest request, ProcedureResponder responder) throws Exception {
      LOG.debug("GOT PROCEDURE");
      Discoverable discoverable = Iterables.getFirst(serviceDiscovered, null);
      LOG.debug("GOT BEFORE DISCOVER NULL");
      if (discoverable != null) {
        LOG.debug("GOT AFTER DISCOVER NOT NULL");
        String hostName = discoverable.getSocketAddress().getHostName();
        int port = discoverable.getSocketAddress().getPort();
        LOG.debug("host: {}, port: {}", hostName, String.valueOf(port));
        String response = doGet(hostName, port);
        LOG.debug("GOT RESPONSE: {}", response);
        responder.sendJson(ProcedureResponse.Code.SUCCESS, response);
      } else {
        responder.sendJson(ProcedureResponse.Code.FAILURE, "ERROR!");
      }
    }

    /**
     *
     * @param hostName
     * @param port
     * @return
     * @throws Exception
     */
    public static String doGet(String hostName, int port) throws Exception {
      try {
        LOG.debug("GOT DOGET: {}", hostName + " " + port);
        URL url = new URL(String.format("http://%s:%d/v1/handle", hostName, port));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (HttpURLConnection.HTTP_OK == conn.getResponseCode()) {
          LOG.debug("GOT HTTP OK IN DOGET");
          try {
            return new String(ByteStreams.toByteArray(conn.getInputStream()), Charsets.UTF_8);
          } finally {
            conn.disconnect();
          }
        }
        LOG.warn("Unexpected response from Catalog Service: {} {}", conn.getResponseCode(), conn.getResponseMessage());
      } catch (Throwable th) {
        LOG.warn("Error while callilng Catalog Service", th);
      }
      return null;
    }
  }
}
