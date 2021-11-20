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

import java.util.List;

/**
 * Tethering request sent from the {@link io.cdap.cdap.internal.tether.RemoteAgentService} to
 * the tethering server.
 */
public class TetherConnectionRequest {
  private final String peer;
  private final List<NamespaceAllocation> namespaceAllocations;

  public TetherConnectionRequest(String peer, List<NamespaceAllocation> namespaceAllocations) {
    this.peer = peer;
    this.namespaceAllocations = namespaceAllocations;
  }

  public String getPeer() {
    return peer;
  }

  public List<NamespaceAllocation> getNamespaceAllocations() {
    return namespaceAllocations;
  }
}
