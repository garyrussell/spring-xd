/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.integration.x.bus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.utils.IntegrationUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;

/**
 * @author Gary Russell
 * @author David Turanski
 * @since 1.0
 */
public class LocalMessageBusTests extends AbstractMessageBusTests {

	@Override
	protected MessageBus getMessageBus() throws Exception {
		LocalMessageBus bus = new LocalMessageBus();
		GenericApplicationContext applicationContext = new GenericApplicationContext();
		applicationContext.getBeanFactory().registerSingleton(
				IntegrationUtils.INTEGRATION_MESSAGE_BUILDER_FACTORY_BEAN_NAME,
				new DefaultMessageBuilderFactory());
		applicationContext.refresh();
		bus.setApplicationContext(applicationContext);
		bus.afterPropertiesSet();
		return bus;
	}

	@Test
	public void testPayloadConversionNotNeededExplicitType() throws Exception {
		LocalMessageBus bus = (LocalMessageBus) getMessageBus();
		verifyPayloadConversion(new Foo(), bus);
	}

	@Test
	public void testNoPayloadConversionByDefault() throws Exception {
		LocalMessageBus bus = (LocalMessageBus) getMessageBus();
		verifyPayloadConversion(new Foo(), bus);
	}


	private void verifyPayloadConversion(final Object expectedValue, final LocalMessageBus bus) {
		DirectChannel myChannel = new DirectChannel();
		bus.bindConsumer("in", myChannel, null);
		DirectChannel input = bus.getBean("in", DirectChannel.class);
		assertNotNull(input);

		final AtomicBoolean msgSent = new AtomicBoolean(false);

		myChannel.subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				assertEquals(expectedValue, message.getPayload());
				msgSent.set(true);
			}
		});

		Message<Foo> msg = MessageBuilder.withPayload(new Foo())
				.setHeader(MessageHeaders.CONTENT_TYPE, MediaType.ALL_VALUE).build();

		input.send(msg);
		assertTrue(msgSent.get());
	}

	static class Foo {

		@Override
		public String toString() {
			return "foo";
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Foo)) {
				return false;
			}
			return this.toString().equals(other.toString());
		}
	}
}
