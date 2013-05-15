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

import java.io.IOException;

import org.springframework.integration.Message;
import org.springframework.integration.ip.tcp.connection.TcpConnectionSupport;

import reactor.tcp.TcpConnectionEvent;
import reactor.tcp.TcpNioConnection;

/**
 * @author Gary Russell
 * @since 1.0
 *
 */
public class ConnectionAdapter extends TcpConnectionSupport {

	private final TcpNioConnection connection;

	public ConnectionAdapter(TcpNioConnection connection) {
		this.connection = connection;
	}

	@Override
	public int hashCode() {
		return this.connection.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return this.connection.equals(obj);
	}

	@Override
	public void registerListener(org.springframework.integration.ip.tcp.connection.TcpListener listener) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() {
		this.connection.close();
	}

	@Override
	public org.springframework.integration.ip.tcp.connection.TcpListener getListener() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isOpen() {
		return this.connection.isOpen();
	}

	@Override
	public void setSingleUse(boolean singleUse) {
		this.connection.setSingleUse(singleUse);
	}

	@Override
	public boolean isSingleUse() {
		return this.connection.isSingleUse();
	}

	@Override
	public boolean isServer() {
		return this.connection.isServer();
	}

	@Override
	public long incrementAndGetConnectionSequence() {
		return this.connection.incrementAndGetConnectionSequence();
	}

	@Override
	public String getHostAddress() {
		return this.connection.getHostAddress();
	}

	@Override
	public String getHostName() {
		return this.connection.getHostName();
	}

	@Override
	public String getConnectionId() {
		return this.connection.getConnectionId();
	}

	public void decode() {
		this.connection.decode();
	}

	public void send(byte[] bytes, int offset, int length) throws IOException {
		this.connection.send(bytes, offset, length);
	}

	public void send(Object object) throws IOException {
		this.connection.send(object);
	}

	public void publishEvent(TcpConnectionEvent event) {
		this.connection.publishEvent(event);
	}

	public void send(byte[] bytes) throws IOException {
		this.connection.send(bytes);
	}

	public long getLastRead() {
		return this.connection.getLastRead();
	}

	public void setLastRead(long lastRead) {
		this.connection.setLastRead(lastRead);
	}

	public long getLastSend() {
		return this.connection.getLastSend();
	}

	@Override
	public int getPort() {
		return this.connection.getPort();
	}

	@Override
	public String toString() {
		return this.connection.toString();
	}

	public void readPacket() {
		this.connection.readPacket();
	}

	public void setUsingDirectBuffers(boolean usingDirectBuffers) {
		this.connection.setUsingDirectBuffers(usingDirectBuffers);
	}

	@Override
	public void send(Message<?> message) throws Exception {
		Object payload = message.getPayload();
		if (payload instanceof byte[]) {
			this.connection.send((byte[]) payload);
		}
		if (payload instanceof String) {
			this.connection.send(((String) payload).getBytes("UTF-8"));
		}
		else {
			this.connection.send(payload);
		}
	}

	@Override
	public Object getPayload() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDeserializerStateKey() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}


}
