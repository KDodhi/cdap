/*
 * Copyright 2012-2013 Continuuity,Inc. All Rights Reserved.
 */

package com.continuuity.internal.app.services;

import com.continuuity.app.guice.AppFabricTestModule;
import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 */
public class AppFabricServerTest {
  private static AppFabricServer server;
  private static CConfiguration configuration;

  @BeforeClass
  public static void before() throws Exception {
    configuration = CConfiguration.create();
    configuration.setInt(Constants.CFG_APP_FABRIC_SERVER_PORT, 45000);
    configuration.set(Constants.CFG_APP_FABRIC_OUTPUT_DIR, System.getProperty("java.io.tmpdir") + "/app");
    configuration.set(Constants.CFG_APP_FABRIC_TEMP_DIR, System.getProperty("java.io.tmpdir") + "/temp");

    Injector injector = Guice.createInjector(new AppFabricTestModule(configuration));

    server = injector.getInstance(AppFabricServer.class);
  }

  @Test
  public void startStopServer() throws Exception {
    ListenableFuture<Service.State> future = server.start();
    Futures.addCallback(future, new FutureCallback<Service.State>() {
      @Override
      public void onSuccess(Service.State result) {
        Assert.assertTrue(true);
      }

      @Override
      public void onFailure(Throwable t) {
        Assert.assertTrue(false);
      }
    });

    Service.State state = future.get();
    Assert.assertTrue(state == Service.State.RUNNING);
    state = server.stopAndWait();
    Assert.assertTrue(state == Service.State.TERMINATED);
  }
}
