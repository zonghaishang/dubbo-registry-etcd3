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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@Ignore
public class JEtcdClientWrapperTest {

    JEtcdClientWrapper clientWrapper;

    @Test
    public void test_path_exists() {
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        clientWrapper.createPersistent(path);
        Assert.assertTrue(clientWrapper.checkExists(path));
        Assert.assertFalse(clientWrapper.checkExists(path + "/noneexits"));
        clientWrapper.delete(path);
    }

    @Test
    public void test_create_emerphal_path() {
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        clientWrapper.createEphemeral(path);
        Assert.assertTrue(clientWrapper.checkExists(path));
        clientWrapper.delete(path);
    }

    @Test
    public void test_grant_lease_then_revoke() {
        long lease = clientWrapper.createLease(1);
        clientWrapper.revokeLease(lease);

        long newLease = clientWrapper.createLease(1);
        LockSupport.parkNanos(this, TimeUnit.SECONDS.toNanos(2));
        // test timeout of lease
        clientWrapper.revokeLease(newLease);
    }

    @Test
    public void test_create_emerphal_path_then_timeout() {
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";

        URL url = URL.valueOf("etcd3://127.0.0.1:2379/org.apache.dubbo.registry.RegistryService")
                .addParameter(Constants.SESSION_TIMEOUT_KEY, 1000);

        JEtcdClientWrapper saved = clientWrapper;

        try {
            clientWrapper = spy(new JEtcdClientWrapper(url));
            clientWrapper.start();

            doAnswer(new Answer() {
                int timeout;

                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    LockSupport.parkNanos(this, TimeUnit.SECONDS.toNanos(2));
                    if (timeout++ > 0) {
                        throw new TimeoutException();
                    }
                    return null;
                }
            }).when(clientWrapper).keepAlive(anyLong());

            try {
                clientWrapper.createEphemeral(path);
            } catch (IllegalStateException ex) {
                Assert.assertEquals("failed to create ephereral by path '" + path + "'", ex.getMessage());
            }

        } finally {
            clientWrapper.doClose();
            clientWrapper = saved;
        }
    }

    @Test
    public void test_get_emerphal_children_path() {
        String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
        String[] children = {
                "/dubbo/org.apache.dubbo.demo.DemoService/providers/service1"
                , "/dubbo/org.apache.dubbo.demo.DemoService/providers/service2"
                , "/dubbo/org.apache.dubbo.demo.DemoService/providers/service3"
                , "/dubbo/org.apache.dubbo.demo.DemoService/providers/service4"
                , "/dubbo/org.apache.dubbo.demo.DemoService/providers/service5/exclude"
        };

        Arrays.stream(children).forEach((child) -> {
            Assert.assertFalse(clientWrapper.checkExists(child));
            clientWrapper.createEphemeral(child);
        });

        List<String> extected = clientWrapper.getChildren(path);

        Assert.assertEquals(4, extected.size());
        extected.stream().forEach((child) -> {
            boolean found = false;
            for (int i = 0; i < children.length; ++i) {
                if (child.equals(children[i])) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue(found);
            clientWrapper.delete(child);
        });
    }

    @Test
    public void test_connect_cluster() {
        URL url = URL.valueOf("etcd3://127.0.0.1:22379/org.apache.dubbo.registry.RegistryService?backup=127.0.0.1:2379,127.0.0.1:32379");
        JEtcdClientWrapper clientWrapper = new JEtcdClientWrapper(url);
        try {
            clientWrapper.start();
            String path = "/dubbo/org.apache.dubbo.demo.DemoService/providers";
            clientWrapper.createEphemeral(path);
            Assert.assertTrue(clientWrapper.checkExists(path));
            Assert.assertFalse(clientWrapper.checkExists(path + "/noneexits"));
            clientWrapper.delete(path);
        } finally {
            clientWrapper.doClose();
        }
    }

    @Before
    public void setUp() {
        URL url = URL.valueOf("etcd3://127.0.0.1:2379/org.apache.dubbo.registry.RegistryService");
        clientWrapper = new JEtcdClientWrapper(url);
        clientWrapper.start();
    }

    @After
    public void tearDown() {
        clientWrapper.doClose();
    }
}
