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
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactoryChain;
import org.springframework.integration.ip.tcp.connection.TcpListener;
import org.springframework.integration.ip.tcp.connection.TcpMessageMapper;
import org.springframework.integration.ip.tcp.connection.TcpSender;
import org.springframework.integration.ip.tcp.connection.TcpSocketSupport;

import reactor.tcp.TcpNioConnectionConfigurer;
import reactor.tcp.TcpNioServerConnectionFactory;
import reactor.tcp.TcpSocketConfigurer;
import reactor.tcp.codec.Codec;

/**
 * @author Gary Russell
 * @since 1.0
 *
 */
public class ServerConnectionFactoryAdapter extends AbstractServerConnectionFactory {

	private final TcpNioServerConnectionFactory reactorServer;

	private volatile TcpListener legacyListener;

	public ServerConnectionFactoryAdapter(TcpNioServerConnectionFactory reactorServer) {
		super(reactorServer.getPort());
		this.reactorServer = reactorServer;
	}

	@Override
	public void start() {
		this.reactorServer.start();
	}

	@Override
	public int hashCode() {
		return this.reactorServer.hashCode();
	}

	@Override
	public boolean isListening() {
		return this.reactorServer.isListening();
	}

	@Override
	public void run() {
		this.reactorServer.run();
	}

	@Override
	public String getLocalAddress() {
		return this.reactorServer.getLocalAddress();
	}

	@Override
	public void setLocalAddress(String localAddress) {
		this.reactorServer.setLocalAddress(localAddress);
	}

	@Override
	public boolean equals(Object obj) {
		return this.reactorServer.equals(obj);
	}

	@Override
	public int getBacklog() {
		return this.reactorServer.getBacklog();
	}

	@Override
	public void setBacklog(int backlog) {
		this.reactorServer.setBacklog(backlog);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.reactorServer.setApplicationEventPublisher(applicationEventPublisher);
	}

	@Override
	public int getSoTimeout() {
		return this.reactorServer.getSoTimeout();
	}

	@Override
	public void setSoTimeout(int soTimeout) {
		this.reactorServer.setSoTimeout(soTimeout);
	}

	@Override
	public int getSoReceiveBufferSize() {
		return this.reactorServer.getSoReceiveBufferSize();
	}

	@Override
	public void setSoReceiveBufferSize(int soReceiveBufferSize) {
		this.reactorServer.setSoReceiveBufferSize(soReceiveBufferSize);
	}

	@Override
	public int getSoSendBufferSize() {
		return this.reactorServer.getSoSendBufferSize();
	}

	@Override
	public void setSoSendBufferSize(int soSendBufferSize) {
		this.reactorServer.setSoSendBufferSize(soSendBufferSize);
	}

	@Override
	public boolean isSoTcpNoDelay() {
		return this.reactorServer.isSoTcpNoDelay();
	}

	@Override
	public void setSoTcpNoDelay(boolean soTcpNoDelay) {
		this.reactorServer.setSoTcpNoDelay(soTcpNoDelay);
	}

	@Override
	public int getSoLinger() {
		return this.reactorServer.getSoLinger();
	}

	@Override
	public void setSoLinger(int soLinger) {
		this.reactorServer.setSoLinger(soLinger);
	}

	@Override
	public void close() {
		this.reactorServer.close();
	}

	@Override
	public boolean isSoKeepAlive() {
		return this.reactorServer.isSoKeepAlive();
	}

	@Override
	public void setSoKeepAlive(boolean soKeepAlive) {
		this.reactorServer.setSoKeepAlive(soKeepAlive);
	}

	public void setUsingDirectBuffers(boolean usingDirectBuffers) {
		this.reactorServer.setUsingDirectBuffers(usingDirectBuffers);
	}

	@Override
	public int getSoTrafficClass() {
		return this.reactorServer.getSoTrafficClass();
	}

	public void setTcpNioConnectionSupport(TcpNioConnectionConfigurer tcpNioSupport) {
		this.reactorServer.setTcpNioConnectionSupport(tcpNioSupport);
	}

	@Override
	public void setSoTrafficClass(int soTrafficClass) {
		this.reactorServer.setSoTrafficClass(soTrafficClass);
	}

	@Override
	public String getHost() {
		return this.reactorServer.getHost();
	}

	@Override
	public int getPort() {
		return this.reactorServer.getPort();
	}

	@Override
	public org.springframework.integration.ip.tcp.connection.TcpListener getListener() {
		return this.legacyListener;
	}

	@Override
	public void registerListener(org.springframework.integration.ip.tcp.connection.TcpListener listener) {
		this.legacyListener = listener;
		this.reactorServer.registerListener(new ListenerAdapter(listener, this));
	}

	@Override
	public void setTaskExecutor(Executor taskExecutor) {
		this.reactorServer.setTaskExecutor(taskExecutor);
	}

	@Override
	public boolean isSingleUse() {
		return this.reactorServer.isSingleUse();
	}

	@Override
	public void setSingleUse(boolean singleUse) {
		this.reactorServer.setSingleUse(singleUse);
	}

	@Override
	public void setLookupHost(boolean lookupHost) {
		this.reactorServer.setLookupHost(lookupHost);
	}

	@Override
	public boolean isLookupHost() {
		return this.reactorServer.isLookupHost();
	}

	@Override
	public void setNioHarvestInterval(int nioHarvestInterval) {
		this.reactorServer.setNioHarvestInterval(nioHarvestInterval);
	}

	public String getFactoryName() {
		return this.reactorServer.getFactoryName();
	}

	public void setFactoryName(String factoryName) {
		this.reactorServer.setFactoryName(factoryName);
	}

	public void setCodec(Codec codec) {
		this.reactorServer.setCodec(codec);
	}

	@Override
	public String toString() {
		return "Adapter for " + this.reactorServer.toString();
	}

	@Override
	public void stop() {
		this.reactorServer.stop();
	}

	@Override
	public int getPhase() {
		return this.reactorServer.getPhase();
	}

	@Override
	public boolean isAutoStartup() {
		return this.reactorServer.isAutoStartup();
	}

	@Override
	public void stop(Runnable callback) {
		this.reactorServer.stop(callback);
	}

	@Override
	public boolean isRunning() {
		return this.reactorServer.isRunning();
	}

	public void setTcpSocketSupport(TcpSocketConfigurer tcpSocketSupport) {
		this.reactorServer.setTcpSocketSupport(tcpSocketSupport);
	}

	@Override
	public List<String> getOpenConnectionIds() {
		return this.reactorServer.getOpenConnectionIds();
	}

	@Override
	public boolean closeConnection(String connectionId) {
		return this.reactorServer.closeConnection(connectionId);
	}

	@Override
	public TcpSender getSender() {
		TcpSender sender = super.getSender();
		if (sender == null) {
			sender = new TcpSender() {

				@Override
				public void removeDeadConnection(org.springframework.integration.ip.tcp.connection.TcpConnection connection) {
					if (ServerConnectionFactoryAdapter.this.logger.isDebugEnabled()) {
						ServerConnectionFactoryAdapter.this.logger.debug("no sender registered for new connection notifications");
					}
				}

				@Override
				public void addNewConnection(org.springframework.integration.ip.tcp.connection.TcpConnection connection) {
					if (ServerConnectionFactoryAdapter.this.logger.isDebugEnabled()) {
						ServerConnectionFactoryAdapter.this.logger.debug("no sender registered for closed connection notifications");
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
		throw new UnsupportedOperationException();
	}

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
