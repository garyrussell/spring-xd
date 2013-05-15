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

package reactor.tcp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

import reactor.Fn;
import reactor.core.R;
import reactor.core.Reactor;
import reactor.fn.Consumer;
import reactor.fn.Event;
import reactor.tcp.codec.Codec;
import reactor.tcp.codec.Codec.DecoderCallback;
import reactor.tcp.codec.DecoderResult;
import reactor.tcp.codec.LineFeedCodec;
import reactor.tcp.codec.StreamingCodec;

/**
 * A TcpConnection that uses and underlying {@link SocketChannel}.
 *
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpNioConnection extends TcpConnectionSupport {

	private final SocketChannel socketChannel;

	private volatile boolean usingDirectBuffers;

	private volatile int maxMessageSize = 60 * 1024;

	private final Buffers readBuffers = new Buffers();

	private volatile Codec codec = new LineFeedCodec();

	private final BlockingQueue<Buffers> outbound = new LinkedBlockingQueue<Buffers>();

	private final Reactor connectionReactor;

	private final Selector ioSelector;

	/**
	 * Constructs a TcpNetConnection for the SocketChannel.
	 * @param socketChannel the socketChannel
	 * @param server if true this connection was created as
	 * a result of an incoming request.
	 */
	public TcpNioConnection(SocketChannel socketChannel, boolean server, boolean lookupHost,
			ApplicationEventPublisher applicationEventPublisher,
			ConnectionFactorySupport connectionFactory) throws IOException {
			super(socketChannel.socket(), server, lookupHost, applicationEventPublisher, connectionFactory);
		this.socketChannel = socketChannel;
		int receiveBufferSize = socketChannel.socket().getReceiveBufferSize();
		if (receiveBufferSize <= 0) {
			receiveBufferSize = this.maxMessageSize;
		}
		this.ioSelector = connectionFactory.getIoSelector();
		this.connectionReactor = R.create();
		this.connectionReactor.on(ConnectionFactorySupport.READ, new Consumer<Event<SelectionKey>> () {

			@Override
			public void accept(Event<SelectionKey> keyEvent) {
				if (logger.isTraceEnabled()) {
					logger.trace("OP_READ for " + TcpNioConnection.this.getConnectionId());
				}
				handleReadSelection(keyEvent.getData());
			}
		});
		this.connectionReactor.on(ConnectionFactorySupport.WRITE, new Consumer<Event<SelectionKey>> () {

			@Override
			public void accept(Event<SelectionKey> keyEvent) {
				if (logger.isTraceEnabled()) {
					logger.trace("OP_WRITE for " + TcpNioConnection.this.getConnectionId());
				}
				handleWriteSelection(ioSelector, keyEvent.getData());
			}
		});
		this.connectionReactor.on(ConnectionFactorySupport.DECODE, new Consumer<Event<TcpNioConnection>>() {

			@Override
			public void accept(Event<TcpNioConnection> connectionEvent) {
				if (logger.isTraceEnabled()) {
					logger.trace("DECODE for " + TcpNioConnection.this.getConnectionId());
				}
				TcpNioConnection.this.decode();
			}
		});

	}

	protected void setCodec(Codec codec) {
		this.codec = codec;
	}

	SocketChannel getSocketChannel() {
		return this.socketChannel;
	}

	Reactor getConnectionReactor() {
		return connectionReactor;
	}

	BlockingQueue<Buffers> getBuffersToWrite() {
		return this.outbound;
	}

	@Override
	public void close() {
		doClose();
	}

	private void doClose() {
		try {
			this.socketChannel.close();
		} catch (Exception e) {}
		super.close();
	}

	@Override
	public boolean isOpen() {
		return this.socketChannel.isOpen();
	}

	protected void handleReadSelection(final SelectionKey key) {
		try {
			this.setLastRead(System.currentTimeMillis());
			try {
				this.readPacket();
			}
			catch (Exception e) {
				if (this.isOpen()) {
					logger.error("Exception on read " +
							this.getConnectionId() + " " +
							e.getMessage());
					this.close();
				}
				else {
					logger.debug("Connection closed");
				}
			}
			if (key.channel().isOpen()) {
				key.interestOps(key.interestOps() | SelectionKey.OP_READ);
				ioSelector.wakeup();
			}
		}
		catch (CancelledKeyException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("key cancelled");
			}
		}
	}

	protected void handleWriteSelection(final Selector selector, final SelectionKey key) {
		try {
			this.doWrite(this.getBuffersToWrite());
		}
		catch (IOException e) {
			logger.error("Exception on write", e);
			this.close();
		}
	}

	public void decode() {
		this.codec.decode(this.readBuffers, new DecoderCallback() {

			@Override
			public void complete(DecoderResult assembly) {
				getListener().onDecode(assembly, TcpNioConnection.this);
			}
		});
	}

	@Override
	public synchronized void send(byte[] bytes, int offset, int length) throws IOException {
		Buffers buffers = this.codec.encode(ByteBuffer.wrap(bytes, offset, length));
		this.outbound.add(buffers);
		doWrite(outbound);
	}

	@Override
	public synchronized void send(Object object) throws IOException {
		Assert.isInstanceOf(StreamingCodec.class, this.codec, "Codec must be streamable");
		StreamingCodec codec = (StreamingCodec) this.codec;
		codec.encode(object, new OutputStream() {

			private volatile ByteBuffer buffer = allocate(2048);

			@Override
			public void write(int b) throws IOException {
				buffer.put((byte) b);
				if (buffer.position() == buffer.limit()) {
					buffer.flip();
					Buffers buffers = new Buffers();
					buffers.add(buffer);
					outbound.add(buffers);
					buffer = allocate(2048);
					doWrite(outbound);
				}
			}
		});
	}

	protected void retrySend() {
		if (this.outbound.size() > 0) {
			try {
				this.doWrite(this.outbound);
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
		}
	}

	protected synchronized void doWrite(BlockingQueue<Buffers> buffersToWrite) throws IOException {
		boolean workToDo = true;
		IOException exceptionToThrow = null;
		while (workToDo) {
			Buffers buffers = buffersToWrite.poll();
			if (buffers == null) {
				workToDo = false;
			}
			else {
				int consumedBuffers = 0;
				int bytesWritten = 0;
				for (ByteBuffer buffer : buffers) {
					try {
						bytesWritten += buffer.remaining();
						this.socketChannel.write(buffer);
					}
					catch (IOException e) {
						exceptionToThrow = e;
						workToDo = false;
					}
					if (buffer.remaining() > 0) {
						bytesWritten -= buffer.remaining();
						workToDo = false;
						// writes are blocked, need to reselect.
						this.getConnectionFactory().writeOpNeeded(this);
					}
					else {
						consumedBuffers++;
					}
				}
				if (logger.isTraceEnabled()) {
					logger.trace(this.getConnectionId() + " Written " + bytesWritten);
				}
				buffers.discardBuffers(consumedBuffers);
			}
		}
		if (exceptionToThrow != null) {
			throw exceptionToThrow;
		}
	}

	@Override
	public int getPort() {
		return this.socketChannel.socket().getPort();
	}

	/**
	 * Allocates a ByteBuffer of the requested length using normal or
	 * direct buffers, depending on the usingDirectBuffers field.
	 */
	protected ByteBuffer allocate(int length) {
		//TODO: cache if using Direct Buffers - not good to churn
		ByteBuffer buffer;
		if (this.usingDirectBuffers) {
			buffer = ByteBuffer.allocateDirect(length);
		} else {
			buffer = ByteBuffer.allocate(length);
		}
		return buffer;
	}

	/**
	 * TODO: Return to cache if {@link #usingDirectBuffers}.
	 * @param buffers
	 */
	protected void deallocate(Buffers buffers) {

	}

	private void doRead() throws Exception {
		ByteBuffer rawBuffer = allocate(maxMessageSize);
		try {
			int len = this.socketChannel.read(rawBuffer);
			if (len < 0) {
				this.closeConnection();
				if (logger.isTraceEnabled()) {
					logger.trace("End of stream");
				}
				return;
			}
			if (logger.isTraceEnabled()) {
				logger.trace("After read:" + rawBuffer.position() + "/" + rawBuffer.limit());
			}
			rawBuffer.flip();
			if (logger.isTraceEnabled()) {
				logger.trace("After flip:" + rawBuffer.position() + "/" + rawBuffer.limit());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Read " + rawBuffer.limit() + " into raw buffer");
			}
			this.fireNewDataEvent(rawBuffer);
		}
		catch (Exception e) {
			this.publishConnectionExceptionEvent(e);
			throw e;
		}
	}

	protected void fireNewDataEvent(ByteBuffer rawBuffer) throws IOException {
		Assert.notNull(rawBuffer, "rawBuffer cannot be null");
		if (logger.isTraceEnabled()) {
			logger.trace(this.getConnectionId() + " Sending " + rawBuffer.limit() + " to codec");
		}
		this.readBuffers.add(rawBuffer);
		this.connectionReactor.notify(ConnectionFactorySupport.DECODE_KEY, Fn.event(this));
	}

	/**
	 * Invoked by the factory when there is data to be read.
	 */
	public void readPacket() {
		if (logger.isDebugEnabled()) {
			logger.debug(this.getConnectionId() + " Reading...");
		}
		try {
			doRead();
		}
		catch (ClosedChannelException cce) {
			if (logger.isDebugEnabled()) {
				logger.debug(this.getConnectionId() + " Channel is closed");
			}
			this.closeConnection();
		}
		catch (Exception e) {
			logger.error("Exception on Read " +
					     this.getConnectionId() + " " +
					     e.getMessage(), e);
			this.closeConnection();
		}
	}

	/**
	 * If true, connection will attempt to use direct buffers where
	 * possible.
	 * @param usingDirectBuffers
	 */
	public void setUsingDirectBuffers(boolean usingDirectBuffers) {
		Assert.isTrue(!usingDirectBuffers, "Not yet implemented - need to avoid churn");
//		this.usingDirectBuffers = usingDirectBuffers;
	}

	protected boolean isUsingDirectBuffers() {
		return usingDirectBuffers;
	}

}
