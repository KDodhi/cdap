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
import com.google.gson.reflect.TypeToken;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.internal.remote.RemoteAuthenticator;
import io.cdap.cdap.common.service.AbstractRetryableScheduledService;
import io.cdap.cdap.common.service.RetryStrategies;
import io.cdap.cdap.spi.data.transaction.TransactionRunner;
import io.cdap.common.http.HttpMethod;
import io.cdap.common.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * The main class to run the remote agent service.
 */
public class TetherAgentService extends AbstractRetryableScheduledService {
  private static final Logger LOG = LoggerFactory.getLogger(TetherAgentService.class);
  private static final Gson GSON = new Gson();
  private static final String CONNECT_CONTROL_CHANNEL = "/v3/tethering/controlchannels/";

  private final CConfiguration cConf;
  private final long connectionInterval;
  private final TetherStore store;
  private final String instanceName;

  @Inject
  TetherAgentService(CConfiguration cConf, TransactionRunner transactionRunner) {
    super(RetryStrategies.fixDelay(cConf.getLong(Constants.Tether.CONNECT_INTERVAL), TimeUnit.SECONDS));
    this.connectionInterval = TimeUnit.SECONDS.toMillis(cConf.getLong(Constants.Tether.CONNECT_INTERVAL));
    this.cConf = cConf;
    this.store = new TetherStore(transactionRunner);
    this.instanceName = cConf.get(Constants.INSTANCE_NAME);
  }

  @Override
  protected void doStartUp() throws InstantiationException, IllegalAccessException {
    Class<? extends RemoteAuthenticator> authClass = cConf.getClass(Constants.Tether.CLIENT_AUTHENTICATOR_CLASS,
                                                                    null,
                                                                    RemoteAuthenticator.class);
    if (authClass != null) {
      RemoteAuthenticator.setDefaultAuthenticator(authClass.newInstance());
    }
  }

  @Override
  protected long runTask() {
    List<PeerInfo> peers;
    try {
      peers = store.getPeers().stream()
        // Ignore peers in REJECTED state.
        .filter(p -> p.getTetherStatus() != TetherStatus.REJECTED)
        .collect(Collectors.toList());
    } catch (IOException e) {
      LOG.warn("Failed to get peer information", e);
      return connectionInterval;
    }
    for (PeerInfo peer : peers) {
      try {
        HttpResponse resp = TetherUtils.sendHttpRequest(HttpMethod.GET, new URI(peer.getEndpoint())
          .resolve(CONNECT_CONTROL_CHANNEL + instanceName));
        switch (resp.getResponseCode()) {
          case HttpURLConnection.HTTP_OK:
            handleResponse(resp, peer);
            break;
          case HttpURLConnection.HTTP_NOT_FOUND:
            handleNotFound(peer);
            break;
          case HttpURLConnection.HTTP_FORBIDDEN:
            handleForbidden(peer);
            break;
          default:
            LOG.error("Peer {} returned unexpected error code {} body {}",
                      peer.getName(), resp.getResponseCode(),
                      resp.getResponseBodyAsString(StandardCharsets.UTF_8));
        }
      } catch (Exception e) {
        LOG.debug("Failed to create control channel to {}", peer, e);
      }
    }
    return connectionInterval;
  }

  private void handleNotFound(PeerInfo peerInfo) throws IOException {
    // Update last connection timestamp.
    store.updatePeerTimestamp(peerInfo.getName());

    TetherConnectionRequest tetherRequest = new TetherConnectionRequest(instanceName,
                                                                        peerInfo.getMetadata()
                                                                          .getNamespaceAllocations());
    try {
      HttpResponse response = TetherUtils.sendHttpRequest(HttpMethod.POST,
                                                          new URI(peerInfo.getEndpoint())
                                                            .resolve(TetherClientHandler.CREATE_TETHER),
                                                          GSON.toJson(tetherRequest));
      if (response.getResponseCode() != 200) {
        LOG.error("Failed to initiate tether with peer {}, response body: {}, code: {}",
                  peerInfo.getName(), response.getResponseBody(), response.getResponseCode());
      }
    } catch (URISyntaxException | IOException e) {
      LOG.error("Failed to send tether request to peer {}, endpoint {}",
                peerInfo.getName(), peerInfo.getEndpoint());
    }
  }

  private void handleForbidden(PeerInfo peerInfo) throws IOException {
    if (peerInfo.getTetherStatus() != TetherStatus.PENDING) {
      LOG.debug("Ignoring tethering rejection message from {}, current state: {}", peerInfo.getName(),
                peerInfo.getTetherStatus());
      return;
    }
    // Set tether status to rejected.
    store.updatePeerStatusAndTimestamp(peerInfo.getName(), TetherStatus.REJECTED);
  }

  private void handleResponse(HttpResponse resp, PeerInfo peerInfo) throws IOException {
    if (peerInfo.getTetherStatus() == TetherStatus.PENDING) {
      LOG.debug("Peer {} transitioned to ACCEPTED state", peerInfo.getName());
      store.updatePeerStatusAndTimestamp(peerInfo.getName(), TetherStatus.ACCEPTED);
    } else {
      // Update last connection timestamp.
      store.updatePeerTimestamp(peerInfo.getName());
    }
    processTetherControlMessage(resp.getResponseBodyAsString(StandardCharsets.UTF_8), peerInfo);
  }

  private void processTetherControlMessage(String message, PeerInfo peerInfo) {
    Type type = new TypeToken<List<TetherControlMessage>>() { }.getType();
    List<TetherControlMessage> tetherControlMessages = GSON.fromJson(message, type);
    for (TetherControlMessage tetherControlMessage : tetherControlMessages) {
      switch (tetherControlMessage.getType()) {
        case KEEPALIVE:
          LOG.trace("Got keeplive from {}", peerInfo.getName());
          break;
        case RUN_PIPELINE:
          // TODO: add processing logic
          break;
      }
    }
  }
}
