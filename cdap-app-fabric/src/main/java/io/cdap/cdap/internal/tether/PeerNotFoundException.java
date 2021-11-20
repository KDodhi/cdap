package io.cdap.cdap.internal.tether;

import io.cdap.cdap.api.common.HttpErrorStatusProvider;
import io.netty.handler.codec.http.HttpResponseStatus;

public class PeerNotFoundException extends Exception implements HttpErrorStatusProvider {
  public PeerNotFoundException(String peerName) {
    super(String.format("Peer %s not found", peerName));
  }

  @Override
  public int getStatusCode() {
    return HttpResponseStatus.NOT_FOUND.code();
  }
}
