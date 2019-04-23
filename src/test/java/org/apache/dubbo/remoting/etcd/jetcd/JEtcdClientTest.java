/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.etcd.jetcd;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.etcd.ChildListener;

import io.etcd.jetcd.common.exception.ClosedClientException;
import io.grpc.Status;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Ignore
public class JEtcdClientTest {

    JEtcdClient client;

    @Test
    public void test_watch_when_create_path() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        String child = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers/demoService1";

        final CountDownLatch notNotified = new CountDownLatch(1);

        ChildListener childListener = (parent, children) -> {
            Assert.assertTrue(children.size() == 1);
            Assert.assertEquals(child.substring(child.lastIndexOf("/") + 1), children.get(0));
            notNotified.countDown();
        };

        client.addChildListener(path, childListener);

        client.createEphemeral(child);
        Assert.assertTrue(notNotified.await(10, TimeUnit.SECONDS));

        client.removeChildListener(path, childListener);
        client.delete(child);
    }

    @Test
    public void test_watch_when_create_wrong_path() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        String child = "/dubbo/com.alibaba.dubbo.demo.DemoService/routers/demoService1";

        final CountDownLatch notNotified = new CountDownLatch(1);

        ChildListener childListener = (parent, children) -> {
            Assert.assertTrue(children.size() == 1);
            Assert.assertEquals(child, children.get(0));
            notNotified.countDown();
        };

        client.addChildListener(path, childListener);

        client.createEphemeral(child);
        Assert.assertFalse(notNotified.await(1, TimeUnit.SECONDS));

        client.removeChildListener(path, childListener);
        client.delete(child);
    }

    @Test
    public void test_watch_when_delete_path() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        String child = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers/demoService1";

        final CountDownLatch notNotified = new CountDownLatch(1);

        ChildListener childListener = (parent, children) -> {
            Assert.assertTrue(children.size() == 0);
            notNotified.countDown();
        };

        client.createEphemeral(child);

        client.addChildListener(path, childListener);
        client.delete(child);

        Assert.assertTrue(notNotified.await(10, TimeUnit.SECONDS));
        client.removeChildListener(path, childListener);
    }

    @Test
    public void test_watch_then_unwatch() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        String child = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers/demoService2";

        final CountDownLatch notNotified = new CountDownLatch(1);
        final CountDownLatch notTwiceNotified = new CountDownLatch(2);

        final Holder notified = new Holder();

        ChildListener childListener = (parent, children) -> {
            Assert.assertTrue(children.size() == 1);
            Assert.assertEquals(child.substring(child.lastIndexOf("/") + 1), children.get(0));
            notNotified.countDown();
            notTwiceNotified.countDown();
            notified.getAndIncrease();
        };

        client.addChildListener(path, childListener);

        client.createEphemeral(child);
        Assert.assertTrue(notNotified.await(15, TimeUnit.SECONDS));

        client.removeChildListener(path, childListener);
        client.delete(child);

        Assert.assertFalse(notTwiceNotified.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(1, notified.value);
        client.delete(child);
    }

    @Test
    public void test_watch_on_unrecoverable_connection() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        JEtcdClient.EtcdWatcher watcher = null;
        try {
            ChildListener childListener = (parent, children) -> {
                Assert.assertEquals(path, parent);
            };
            client.addChildListener(path, childListener);
            watcher = client.getChildListener(path, childListener);
            watcher.watchRequest.onError(Status.ABORTED.withDescription("connection error").asRuntimeException());

            watcher.watchRequest.onNext(watcher.nextRequest());
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("call was cancelled"));
        }
    }

    @Test
    public void test_watch_on_recoverable_connection() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/connection";
        String child = "/dubbo/com.alibaba.dubbo.demo.DemoService/connection/demoService1";

        final CountDownLatch notNotified = new CountDownLatch(1);
        final CountDownLatch notTwiceNotified = new CountDownLatch(2);
        final Holder notified = new Holder();
        ChildListener childListener = (parent, children) -> {
            notTwiceNotified.countDown();
            switch (notified.increaseAndGet()) {
                case 1: {
                    notNotified.countDown();
                    Assert.assertTrue(children.size() == 1);
                    Assert.assertEquals(child.substring(child.lastIndexOf("/") + 1), children.get(0));
                    break;
                }
                case 2: {
                    Assert.assertTrue(children.size() == 0);
                    Assert.assertEquals(path, parent);
                    break;
                }
                default:
                    Assert.fail("two many callback invoked.");
            }
        };

        client.addChildListener(path, childListener);
        client.createEphemeral(child);

        // make sure first time callback successfully
        Assert.assertTrue(notNotified.await(15, TimeUnit.SECONDS));

        // connection error causes client to release all resources including current watcher
        JEtcdClient.EtcdWatcher watcher = client.getChildListener(path, childListener);
        watcher.onError(Status.UNAVAILABLE.withDescription("temporary connection issue").asRuntimeException());

        // trigger delete after unavailable
        client.delete(child);
        Assert.assertTrue(notTwiceNotified.await(15, TimeUnit.SECONDS));

        client.removeChildListener(path, childListener);
    }

    @Test
    public void test_watch_after_client_closed() throws InterruptedException {

        String path = "/dubbo/com.alibaba.dubbo.demo.DemoService/providers";
        client.close();

        try {
            client.addChildListener(path, (parent, children) -> {
                Assert.assertEquals(path, parent);
            });
        } catch (ClosedClientException e) {
            Assert.assertEquals("watch client has been closed, path '" + path + "'", e.getMessage());
        }
    }

    @Before
    public void setUp() {
        // timeout in 15 seconds.
        URL url = URL.valueOf("etcd3://127.0.0.1:2379/com.alibaba.dubbo.registry.RegistryService")
                .addParameter(Constants.SESSION_TIMEOUT_KEY, 15000);

        client = new JEtcdClient(url);
    }

    @After
    public void tearDown() {
        client.close();
    }

    static class Holder {

        volatile int value;

        synchronized int getAndIncrease() {
            return value++;
        }

        synchronized int increaseAndGet() {
            return ++value;
        }
    }
}
