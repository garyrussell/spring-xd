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

package org.springframework.integration.x.coap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.junit.Test;

import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;


/**
 *
 * @author Gary Russell
 */
public class CfCoapInboundChannelAdapterTests {

	@Test
	public void test() throws Exception {
		CfCoapInboundChannelAdapter adapter = new CfCoapInboundChannelAdapter(15683);
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		adapter.start();

		doSend();
		Message<?> request = outputChannel.receive(10000);
		assertNotNull(request);
		assertEquals("bar", request.getPayload());
		System.out.println(request);
		adapter.stop();
	}

	private void doSend() throws Exception {
		URI uri = new URI("coap", null, "localhost", 15683, "/xd", null, null);
		CoapClient client = new CoapClient(uri);

		CoapResponse response = client.post("bar".getBytes(), 0);
		assertEquals(ResponseCode.CREATED, response.getCode());
	}

	/**
	 * xd:>stream create foo --definition "coap | log" --deploy
	 *
	 * @param args args
	 * @throws Exception ex
	 */
	public static void main(String[] args) throws Exception {
		new CfCoapInboundChannelAdapterTests().doSend();
		System.exit(0);
	}
}
