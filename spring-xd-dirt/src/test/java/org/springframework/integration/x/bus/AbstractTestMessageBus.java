/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.integration.x.bus;

import java.util.HashSet;
import java.util.Set;

import org.springframework.messaging.MessageChannel;
import org.springframework.xd.dirt.core.ModuleDeploymentProperties;


/**
 * Abstract class that adds test support for {@link MessageBus}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Gary Russell
 */
public abstract class AbstractTestMessageBus implements MessageBus {

	protected Set<String> queues = new HashSet<String>();

	protected Set<String> topics = new HashSet<String>();

	private final MessageBusSupport messageBus;

	public AbstractTestMessageBus(MessageBusSupport messageBus) {
		messageBus.setBeanFactory(BusTestUtils.MOCK_BF);
		this.messageBus = messageBus;
	}

	@Override
	public void bindConsumer(String name, MessageChannel moduleInputChannel, ModuleDeploymentProperties properties) {
		messageBus.bindConsumer(name, moduleInputChannel, properties);
		queues.add(name);
	}

	@Override
	public void bindPubSubConsumer(String name, MessageChannel inputChannel, ModuleDeploymentProperties properties) {
		messageBus.bindPubSubConsumer(name, inputChannel, properties);
		addTopic(name);
	}

	@Override
	public void bindProducer(String name, MessageChannel moduleOutputChannel, ModuleDeploymentProperties properties) {
		messageBus.bindProducer(name, moduleOutputChannel, properties);
		queues.add(name);
	}

	@Override
	public void bindPubSubProducer(String name, MessageChannel outputChannel, ModuleDeploymentProperties properties) {
		messageBus.bindPubSubProducer(name, outputChannel, properties);
		addTopic(name);
	}

	@Override
	public void bindRequestor(String name, MessageChannel requests, MessageChannel replies,
			ModuleDeploymentProperties properties) {
		messageBus.bindRequestor(name, requests, replies, properties);
		queues.add(name + ".requests");
	}

	@Override
	public void bindReplier(String name, MessageChannel requests, MessageChannel replies,
			ModuleDeploymentProperties properties) {
		messageBus.bindReplier(name, requests, replies, properties);
		queues.add(name + ".requests");
	}

	private void addTopic(String topicName) {
		topics.add("topic." + topicName);
	}

	public MessageBus getCoreMessageBus() {
		return messageBus;
	}

	public abstract void cleanup();

	@Override
	public void unbindConsumers(String name) {
		messageBus.unbindConsumers(name);
	}

	@Override
	public void unbindProducers(String name) {
		messageBus.unbindProducers(name);
	}

	@Override
	public void unbindConsumer(String name, MessageChannel channel) {
		messageBus.unbindConsumer(name, channel);
	}

	@Override
	public void unbindProducer(String name, MessageChannel channel) {
		messageBus.unbindProducer(name, channel);
	}

	public MessageBus getMessageBus() {
		return this.messageBus;
	}

}
