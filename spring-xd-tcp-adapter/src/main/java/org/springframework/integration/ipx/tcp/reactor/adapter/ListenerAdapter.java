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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.integration.Message;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.ip.tcp.connection.AbstractConnectionFactory;
import org.springframework.integration.support.MessageBuilder;

import reactor.tcp.ConnectionAwareTcpListener;
import reactor.tcp.TcpConnection;
import reactor.tcp.TcpNioConnection;
import reactor.tcp.codec.Assembly;
import reactor.tcp.codec.DecodedObject;
import reactor.tcp.codec.DecoderResult;

/**
 * @author Gary Russell
 * @since 1.0
 *
 */
public class ListenerAdapter implements ConnectionAwareTcpListener {

	private final org.springframework.integration.ip.tcp.connection.TcpListener listener;

	private final AbstractConnectionFactory connectionFactory;

	private final Map<TcpConnection, ConnectionAdapter>
		connectionMap = new ConcurrentHashMap<TcpConnection, ConnectionAdapter>();

	public ListenerAdapter(org.springframework.integration.ip.tcp.connection.TcpListener listener,
			AbstractConnectionFactory factory) {
		this.listener = listener;
		this.connectionFactory = factory;
	}

	public ConnectionAdapter lookupFor(TcpConnection connection) {
		return this.connectionMap.get(connection);
	}

	@Override
	public void onDecode(DecoderResult result, TcpConnection connection) {
		Object payload;
		if (result instanceof Assembly) {
			payload = ((Assembly) result).asBytes();
		}
		else if (result instanceof DecodedObject) {
			payload = ((DecodedObject) result).getObject();
		}
		else {
			payload = result;
		}
		if (payload != null) {
			MessageBuilder<Object> messageBuilder = MessageBuilder.withPayload(payload);
			String connectionId = connection.getConnectionId();
			messageBuilder
				.setHeader(IpHeaders.HOSTNAME, connection.getHostName())
				.setHeader(IpHeaders.IP_ADDRESS, connection.getHostAddress())
				.setHeader(IpHeaders.REMOTE_PORT, connection.getPort())
				.setHeader(IpHeaders.CONNECTION_ID, connectionId)
				.setCorrelationId(connectionId)
				.setSequenceNumber((int) connection.incrementAndGetConnectionSequence());
			Map<String, ?> customHeaders = this.supplyCustomHeaders(connection);
			if (customHeaders != null) {
				messageBuilder.copyHeadersIfAbsent(customHeaders);
			}
			Message<?> message = messageBuilder.build();
			this.listener.onMessage(message);
		}
	}

	public Map<String, ?> supplyCustomHeaders(TcpConnection connection) {
		return null;
	}

	@Override
	public void newConnection(TcpConnection connection) {
		ConnectionAdapter connectionAdapter = new ConnectionAdapter((TcpNioConnection) connection);
		this.connectionMap.put(connection, connectionAdapter);
		this.connectionFactory.getSender().addNewConnection(connectionAdapter);
	}

	@Override
	public void connectionClosed(TcpConnection connection) {
		ConnectionAdapter connectionAdapter = this.connectionMap.remove(connection);
		if (connectionAdapter != null) {
			this.connectionFactory.getSender().removeDeadConnection(connectionAdapter);
		}
	}


}
