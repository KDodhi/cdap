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

package io.cdap.cdap.common.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowControlChannelHandler extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControlChannelHandler.class);

  private boolean canPass = false;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    LOG.error(">>>>>>> in channel read");
    if (msg instanceof HttpRequest) {
      LOG.error(">>>>>>> " + ((HttpRequest) msg).uri());
      if (((HttpRequest) msg).uri().contains("/start")) {
        canPass = !canPass;
        if (!canPass) {
          throw new IllegalArgumentException(">>>>>>>>>> Cannot pass;");
        }
      } else {
        LOG.error(">>>>>>> in channel read -- not containing start");
      }
    } else {
      LOG.error(">>>>>>> in channel read -- not http request");
    }

    ctx.fireChannelRead(msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    LOG.error(">>>>>> Got exception: {}", cause.getMessage(), cause);

  }
}
