/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.ipx.tcp.reactor.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.message.GenericMessage;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Gary Russell
 * @since 1.0
 *
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class AdapterTests {

	@Autowired
	MessageChannel toTCPGateway;

	@Autowired
	PollableChannel fromTCPGateway;

	@Autowired
	MessageChannel toTCPAdapter;

	@Autowired
	PollableChannel fromTCPAdapter;

	@Test
	public void testGateways() {
		this.toTCPGateway.send(new GenericMessage<String>("foo"));
		Message<?> reply = this.fromTCPGateway.receive(3000);
		assertNotNull(reply);
		assertEquals("echo:foo", new String((byte[]) reply.getPayload()));
	}

	@Test
	public void testAdapters() {
		this.toTCPAdapter.send(new GenericMessage<String>("foo"));
		Message<?> reply = this.fromTCPAdapter.receive(3000);
		assertNotNull(reply);
		assertEquals("echo:foo", new String((byte[]) reply.getPayload()));
	}
}
