/*
 * Copyright © 2021 Cask Data, Inc.
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

package io.cdap.cdap.internal.tether;

import com.google.gson.Gson;
import io.cdap.cdap.common.BadRequestException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.internal.remote.RemoteAuthenticator;
import io.cdap.common.http.HttpMethod;
import io.cdap.common.http.HttpResponse;
import io.cdap.http.AbstractHttpHandler;
import io.cdap.http.HandlerContext;
import io.cdap.http.HttpResponder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * {@link io.cdap.http.HttpHandler} to manage tethering client v3 REST APIs
 */
@Path(Constants.Gateway.API_VERSION_3)
public class TetherClientHandler extends AbstractHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TetherClientHandler.class);
  private static final Gson GSON = new Gson();
  static final String CREATE_TETHER = "/v3/tethering/connect";

  private final TetherStore store;
  private final CConfiguration cConf;
  private final String instanceName;

  @Inject
  TetherClientHandler(CConfiguration cConf, TetherStore store) {
    this.store = store;
    this.cConf = cConf;
    this.instanceName = cConf.get(Constants.INSTANCE_NAME);
  }

  @Override
  public void init(HandlerContext context) {
    super.init(context);
    Class<? extends RemoteAuthenticator> authClass = cConf.getClass(Constants.Tether.CLIENT_AUTHENTICATOR_CLASS,
                                                                    null,
                                                                    RemoteAuthenticator.class);
    if (authClass != null) {
      try {
        RemoteAuthenticator.setDefaultAuthenticator(authClass.newInstance());
      } catch (Exception e) {
        LOG.error("Failed to set default authenticator", e);
      }
    }
  }

  /**
   * Initiates tethering with the server.
   */
  @POST
  @Path("/tethering/create")
  public void createTether(FullHttpRequest request, HttpResponder responder) throws Exception {
    String content = request.content().toString(StandardCharsets.UTF_8);
    TetherCreationRequest tetherCreationRequest = GSON.fromJson(content, TetherCreationRequest.class);

    PeerInfo peer = null;
    try {
      peer = store.getPeer(tetherCreationRequest.getPeer());
    } catch (PeerNotFoundException e) {
      // Do nothing, expected if peer is not already configured.
    }
    if (peer != null) {
      LOG.info("Peer {} is already present in state {}, ignoring tethering request",
               peer.getName(), peer.getTetherStatus());
      responder.sendStatus(HttpResponseStatus.OK);
      return;
    }

    List<NamespaceAllocation> namespaces = tetherCreationRequest.getNamespaceAllocations();
    TetherConnectionRequest tetherConnectionRequest = new TetherConnectionRequest(instanceName,
                                                                                  namespaces);
    if (tetherCreationRequest.getEndpoint() == null) {
      throw new BadRequestException("Endpoint is null");
    }
    URI endpoint = new URI(tetherCreationRequest.getEndpoint());
    HttpResponse response = TetherUtils.sendHttpRequest(HttpMethod.POST, endpoint.resolve(CREATE_TETHER),
                                                        GSON.toJson(tetherConnectionRequest));
    if (response.getResponseCode() != 200) {
      LOG.error("Failed to send tether request, body: {}, code: {}",
                response.getResponseBody(), response.getResponseCode());
      responder.sendStatus(HttpResponseStatus.valueOf(response.getResponseCode()));
      return;
    }

    PeerMetadata peerMetadata = new PeerMetadata(namespaces, tetherCreationRequest.getMetadata());
    PeerInfo peerInfo = new PeerInfo(tetherCreationRequest.getPeer(), tetherCreationRequest.getEndpoint(),
                                     TetherStatus.PENDING, peerMetadata);
    store.addPeer(peerInfo);
    responder.sendStatus(HttpResponseStatus.OK);
  }
}
