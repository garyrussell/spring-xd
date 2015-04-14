/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.x.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.x.coap.NcoapInboundChannelAdapter;
import org.springframework.messaging.Message;

import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.communication.dispatching.client.ClientCallback;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.MessageType;


/**
 *
 * @author Gary Russell
 */
public class NcoapInboundChannelAdapterTests {

	@Test
	public void test() throws Exception {
		NcoapInboundChannelAdapter adapter = new NcoapInboundChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		adapter.start();

		doSend();
		Message<?> request = outputChannel.receive(10000);
		assertNotNull(request);
		assertEquals("foo", request.getPayload());
		System.out.println(request);
		adapter.stop();
	}

	private void doSend() throws Exception {
		URI webserviceURI = new URI("coap", null, "localhost", 5683, "/xd", null, null);
		CoapRequest coapRequest = new CoapRequest(MessageType.Name.CON, MessageCode.Name.POST, webserviceURI);
		coapRequest.setContent("foo".getBytes());

		CoapClientApplication client = new CoapClientApplication();

		final AtomicReference<CoapResponse> response = new AtomicReference<>();
		final CountDownLatch latch = new CountDownLatch(1);
		ClientCallback clientCallback = new ClientCallback() {

			@Override
			public void processCoapResponse(CoapResponse coapResponse) {
				response.set(coapResponse);
				latch.countDown();
			}
		};
		InetSocketAddress remoteEndpoint = new InetSocketAddress(InetAddress.getByName("localhost"), 5683);
		client.sendCoapRequest(coapRequest, clientCallback, remoteEndpoint);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		assertNotNull(response.get());
	}

	/**
	 * xd:>stream create foo --definition "coap | log" --deploy
	 *
	 * @param args args
	 * @throws Exception ex
	 */
	public static void main(String[] args) throws Exception {
		new NcoapInboundChannelAdapterTests().doSend();
		System.exit(0);
	}
}
