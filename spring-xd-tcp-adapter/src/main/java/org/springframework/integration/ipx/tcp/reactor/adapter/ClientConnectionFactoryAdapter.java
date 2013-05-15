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

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactoryChain;
import org.springframework.integration.ip.tcp.connection.TcpConnectionSupport;
import org.springframework.integration.ip.tcp.connection.TcpListener;
import org.springframework.integration.ip.tcp.connection.TcpMessageMapper;
import org.springframework.integration.ip.tcp.connection.TcpSender;
import org.springframework.integration.ip.tcp.connection.TcpSocketSupport;

import reactor.tcp.TcpConnection;
import reactor.tcp.TcpNioClientConnectionFactory;
import reactor.tcp.TcpNioConnectionConfigurer;
import reactor.tcp.TcpSocketConfigurer;
import reactor.tcp.codec.Codec;

/**
 * @author Gary Russell
 * @since 1.0
 *
 */
public class ClientConnectionFactoryAdapter extends AbstractClientConnectionFactory {

	private final TcpNioClientConnectionFactory reactorClient;

	private volatile TcpListener legacyListener;

	private volatile ListenerAdapter listenerAdapter;

	public ClientConnectionFactoryAdapter(TcpNioClientConnectionFactory reactorClient) {
		super(reactorClient.getHost(), reactorClient.getPort());
		this.reactorClient = reactorClient;
	}

	@Override
	public TcpConnectionSupport getConnection() throws Exception {
		return this.listenerAdapter.lookupFor(this.reactorClient.getConnection());
	}

	@Override
	public int hashCode() {
		return this.reactorClient.hashCode();
	}

	public void forceClose(TcpConnection connection) {
		this.reactorClient.forceClose(connection);
	}

	public void setUsingDirectBuffers(boolean usingDirectBuffers) {
		this.reactorClient.setUsingDirectBuffers(usingDirectBuffers);
	}

	public void setTcpNioConnectionSupport(TcpNioConnectionConfigurer tcpNioSupport) {
		this.reactorClient.setTcpNioConnectionSupport(tcpNioSupport);
	}

	@Override
	public boolean equals(Object obj) {
		return this.reactorClient.equals(obj);
	}

	@Override
	public void close() {
		this.reactorClient.close();
	}

	@Override
	public void start() {
		this.reactorClient.start();
	}

	public void run() {
		this.reactorClient.run();
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.reactorClient.setApplicationEventPublisher(applicationEventPublisher);
	}

	@Override
	public int getSoTimeout() {
		return this.reactorClient.getSoTimeout();
	}

	@Override
	public void setSoTimeout(int soTimeout) {
		this.reactorClient.setSoTimeout(soTimeout);
	}

	@Override
	public int getSoReceiveBufferSize() {
		return this.reactorClient.getSoReceiveBufferSize();
	}

	@Override
	public void setSoReceiveBufferSize(int soReceiveBufferSize) {
		this.reactorClient.setSoReceiveBufferSize(soReceiveBufferSize);
	}

	@Override
	public int getSoSendBufferSize() {
		return this.reactorClient.getSoSendBufferSize();
	}

	@Override
	public void setSoSendBufferSize(int soSendBufferSize) {
		this.reactorClient.setSoSendBufferSize(soSendBufferSize);
	}

	@Override
	public boolean isSoTcpNoDelay() {
		return this.reactorClient.isSoTcpNoDelay();
	}

	@Override
	public void setSoTcpNoDelay(boolean soTcpNoDelay) {
		this.reactorClient.setSoTcpNoDelay(soTcpNoDelay);
	}

	@Override
	public int getSoLinger() {
		return this.reactorClient.getSoLinger();
	}

	@Override
	public void setSoLinger(int soLinger) {
		this.reactorClient.setSoLinger(soLinger);
	}

	@Override
	public boolean isSoKeepAlive() {
		return this.reactorClient.isSoKeepAlive();
	}

	@Override
	public void setSoKeepAlive(boolean soKeepAlive) {
		this.reactorClient.setSoKeepAlive(soKeepAlive);
	}

	@Override
	public int getSoTrafficClass() {
		return this.reactorClient.getSoTrafficClass();
	}

	@Override
	public void setSoTrafficClass(int soTrafficClass) {
		this.reactorClient.setSoTrafficClass(soTrafficClass);
	}

	@Override
	public String getHost() {
		return this.reactorClient.getHost();
	}

	@Override
	public int getPort() {
		return this.reactorClient.getPort();
	}

	@Override
	public org.springframework.integration.ip.tcp.connection.TcpListener getListener() {
		return this.legacyListener;
	}

	@Override
	public void registerListener(org.springframework.integration.ip.tcp.connection.TcpListener listener) {
		this.legacyListener = listener;
		this.listenerAdapter = new ListenerAdapter(listener, this);
		this.reactorClient.registerListener(this.listenerAdapter);
	}

	@Override
	public void setTaskExecutor(Executor taskExecutor) {
		this.reactorClient.setTaskExecutor(taskExecutor);
	}

	@Override
	public boolean isSingleUse() {
		return this.reactorClient.isSingleUse();
	}

	@Override
	public void setSingleUse(boolean singleUse) {
		this.reactorClient.setSingleUse(singleUse);
	}

	@Override
	public void setLookupHost(boolean lookupHost) {
		this.reactorClient.setLookupHost(lookupHost);
	}

	@Override
	public boolean isLookupHost() {
		return this.reactorClient.isLookupHost();
	}

	@Override
	public void setNioHarvestInterval(int nioHarvestInterval) {
		this.reactorClient.setNioHarvestInterval(nioHarvestInterval);
	}

	public String getFactoryName() {
		return this.reactorClient.getFactoryName();
	}

	public void setFactoryName(String factoryName) {
		this.reactorClient.setFactoryName(factoryName);
	}

	public void setCodec(Codec codec) {
		this.reactorClient.setCodec(codec);
	}

	@Override
	public String toString() {
		return "Adapter for " + this.reactorClient.toString();
	}

	@Override
	public void stop() {
		this.reactorClient.stop();
	}

	@Override
	public int getPhase() {
		return this.reactorClient.getPhase();
	}

	@Override
	public boolean isAutoStartup() {
		return this.reactorClient.isAutoStartup();
	}

	@Override
	public void stop(Runnable callback) {
		this.reactorClient.stop(callback);
	}

	@Override
	public boolean isRunning() {
		return this.reactorClient.isRunning();
	}

	public void setTcpSocketSupport(TcpSocketConfigurer tcpSocketSupport) {
		this.reactorClient.setTcpSocketSupport(tcpSocketSupport);
	}

	@Override
	public List<String> getOpenConnectionIds() {
		return this.reactorClient.getOpenConnectionIds();
	}

	@Override
	public boolean closeConnection(String connectionId) {
		return this.reactorClient.closeConnection(connectionId);
	}

	@Override
	protected TcpConnectionSupport obtainConnection() throws Exception {
		throw new UnsupportedOperationException("should not be called directly");
	}

	@Override
	public void forceClose(org.springframework.integration.ip.tcp.connection.TcpConnection connection) {
		// TODO Auto-generated method stub
	}

	@Override
	public TcpSender getSender() {
		TcpSender sender = super.getSender();
		if (sender == null) {
			sender = new TcpSender() {

				@Override
				public void removeDeadConnection(org.springframework.integration.ip.tcp.connection.TcpConnection connection) {
					if (ClientConnectionFactoryAdapter.this.logger.isDebugEnabled()) {
						ClientConnectionFactoryAdapter.this.logger.debug("no sender registered for new connection notifications");
					}
				}

				@Override
				public void addNewConnection(org.springframework.integration.ip.tcp.connection.TcpConnection connection) {
					if (ClientConnectionFactoryAdapter.this.logger.isDebugEnabled()) {
						ClientConnectionFactoryAdapter.this.logger.debug("no sender registered for closed connection notifications");
					}
				}
			};
		}
		return sender;
	}

	@Override
	public Serializer<?> getSerializer() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Deserializer<?> getDeserializer() {
		throw new UnsupportedOperationException();	}

	@Override
	public TcpMessageMapper getMapper() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setDeserializer(Deserializer<?> deserializer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setSerializer(Serializer<?> serializer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setMapper(TcpMessageMapper mapper) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setInterceptorFactoryChain(TcpConnectionInterceptorFactoryChain interceptorFactoryChain) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setTcpSocketSupport(TcpSocketSupport tcpSocketSupport) {
		throw new UnsupportedOperationException();
	}

}
