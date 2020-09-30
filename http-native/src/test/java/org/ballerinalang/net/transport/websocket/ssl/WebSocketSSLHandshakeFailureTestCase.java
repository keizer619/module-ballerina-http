/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.transport.websocket.ssl;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.ballerinalang.net.transport.contract.HttpWsConnectorFactory;
import org.ballerinalang.net.transport.contract.ServerConnector;
import org.ballerinalang.net.transport.contract.ServerConnectorFuture;
import org.ballerinalang.net.transport.contract.config.ListenerConfiguration;
import org.ballerinalang.net.transport.contract.websocket.ClientHandshakeFuture;
import org.ballerinalang.net.transport.contract.websocket.ClientHandshakeListener;
import org.ballerinalang.net.transport.contract.websocket.WebSocketClientConnector;
import org.ballerinalang.net.transport.contract.websocket.WebSocketClientConnectorConfig;
import org.ballerinalang.net.transport.contract.websocket.WebSocketConnection;
import org.ballerinalang.net.transport.contractimpl.DefaultHttpWsConnectorFactory;
import org.ballerinalang.net.transport.message.HttpCarbonResponse;
import org.ballerinalang.net.transport.util.TestUtil;
import org.ballerinalang.net.transport.websocket.client.WebSocketTestClientConnectorListener;
import org.ballerinalang.net.transport.websocket.server.WebSocketTestServerConnectorListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLException;

import static org.ballerinalang.net.transport.util.TestUtil.WEBSOCKET_REMOTE_SERVER_PORT;
import static org.ballerinalang.net.transport.util.TestUtil.WEBSOCKET_SECURE_REMOTE_SERVER_URL;
import static org.ballerinalang.net.transport.util.TestUtil.WEBSOCKET_TEST_IDLE_TIMEOUT;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Tests the failure scenario of SSL handshake in WebSocket.
 */
public class WebSocketSSLHandshakeFailureTestCase {

    private String password = "ballerina";
    private String tlsStoreType = "PKCS12";
    private HttpWsConnectorFactory httpConnectorFactory;
    private ServerConnector serverConnector;

    @BeforeClass
    public void setup() throws InterruptedException {
        httpConnectorFactory = new DefaultHttpWsConnectorFactory();

        ListenerConfiguration listenerConfiguration = getListenerConfiguration();
        serverConnector = httpConnectorFactory
                .createServerConnector(TestUtil.getDefaultServerBootstrapConfig(), listenerConfiguration);
        ServerConnectorFuture future = serverConnector.start();
        future.setWebSocketConnectorListener(new WebSocketTestServerConnectorListener());
        future.sync();
    }

    private ListenerConfiguration getListenerConfiguration() {
        ListenerConfiguration listenerConfiguration = new ListenerConfiguration();
        listenerConfiguration.setPort(WEBSOCKET_REMOTE_SERVER_PORT);
        //set PKCS12 keystore to ballerina server.
        String keyStoreFile = "/simple-test-config/wso2carbon.p12";
        listenerConfiguration.setKeyStoreFile(TestUtil.getAbsolutePath(keyStoreFile));
        listenerConfiguration.setScheme("https");
        listenerConfiguration.setKeyStorePass(password);
        listenerConfiguration.setTLSStoreType(tlsStoreType);
        return listenerConfiguration;
    }

    private WebSocketClientConnectorConfig getWebSocketClientConnectorConfigWithSSL() {
        WebSocketClientConnectorConfig clientConnectorConfig =
                new WebSocketClientConnectorConfig(WEBSOCKET_SECURE_REMOTE_SERVER_URL);
        String trustStoreFile = "/simple-test-config/cacerts.p12";
        clientConnectorConfig.setTrustStoreFile(TestUtil.getAbsolutePath(trustStoreFile));
        clientConnectorConfig.setTrustStorePass("cacertspassword");
        clientConnectorConfig.setTLSStoreType(tlsStoreType);
        return clientConnectorConfig;
    }

    @Test
    public void testClientConnectionWithSSL() throws Throwable {
        WebSocketClientConnector webSocketClientConnector =
                httpConnectorFactory.createWsClientConnector(getWebSocketClientConnectorConfigWithSSL());
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<WebSocketConnection> webSocketConnectionAtomicReference = new AtomicReference<>();
        AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();
        ClientHandshakeFuture handshakeFuture = webSocketClientConnector.connect();
        WebSocketTestClientConnectorListener clientConnectorListener = new WebSocketTestClientConnectorListener();
        handshakeFuture.setWebSocketConnectorListener(clientConnectorListener);
        handshakeFuture.setClientHandshakeListener(new ClientHandshakeListener() {
            @Override public void onSuccess(WebSocketConnection webSocketConnection, HttpCarbonResponse response) {
                webSocketConnectionAtomicReference.set(webSocketConnection);
                countDownLatch.countDown();
            }

            @Override public void onError(Throwable throwable, HttpCarbonResponse response) {
                throwableAtomicReference.set(throwable);
                countDownLatch.countDown();
            }
        });
        countDownLatch.await(WEBSOCKET_TEST_IDLE_TIMEOUT, SECONDS);
        Throwable throwable = throwableAtomicReference.get();

        Assert.assertNull(webSocketConnectionAtomicReference.get());
        Assert.assertNotNull(throwable);
        System.out.println("----------------------------------------------*****************************");
        String errorMessage = throwable.getMessage();
        System.out.println("************************" + errorMessage);
        System.out.println("**************");
        throwable.printStackTrace();
        System.out.println("**************");
//        Assert.assertEquals(throwable.getMessage(), "General SSLEngine problem");
        // The exception message is java version dependent, hence asserting the exception class
        Assert.assertTrue(throwable instanceof SSLException);
    }

    @AfterClass
    public void cleanup() throws InterruptedException {
        serverConnector.stop();
        httpConnectorFactory.shutdown();
    }
}
