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

package org.springframework.xd.dirt.integration.kafka;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import kafka.admin.AdminUtils;
import kafka.api.OffsetRequest;
import kafka.api.TopicMetadata;
import kafka.common.ErrorMapping;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.producer.Producer;
import kafka.producer.DefaultPartitioner;
import kafka.serializer.Decoder;
import kafka.serializer.DefaultDecoder;
import kafka.serializer.DefaultEncoder;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import scala.collection.Seq;

import org.springframework.context.Lifecycle;
import org.springframework.expression.Expression;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.kafka.core.ConnectionFactory;
import org.springframework.integration.kafka.core.DefaultConnectionFactory;
import org.springframework.integration.kafka.core.Partition;
import org.springframework.integration.kafka.core.ZookeeperConfiguration;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.integration.kafka.listener.MetadataStoreOffsetManager;
import org.springframework.integration.kafka.support.ProducerConfiguration;
import org.springframework.integration.kafka.support.ProducerFactoryBean;
import org.springframework.integration.kafka.support.ProducerMetadata;
import org.springframework.integration.kafka.support.ZookeeperConnect;
import org.springframework.integration.x.kafka.KafkaTopicMetadataStore;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.xd.dirt.integration.bus.AbstractBusPropertiesAccessor;
import org.springframework.xd.dirt.integration.bus.Binding;
import org.springframework.xd.dirt.integration.bus.BusProperties;
import org.springframework.xd.dirt.integration.bus.EmbeddedHeadersMessageConverter;
import org.springframework.xd.dirt.integration.bus.MessageBusSupport;
import org.springframework.xd.dirt.integration.bus.serializer.MultiTypeCodec;

/**
 * A message bus that uses Kafka as the underlying middleware.
 * The general implementation mapping between XD concepts and Kafka concepts is as follows:
 * <table>
 * <tr>
 * <th>Stream definition</th><th>Kafka topic</th><th>Kafka partitions</th><th>Notes</th>
 * </tr>
 * <tr>
 * <td>foo = "http | log"</td><td>foo.0</td><td>1 partition</td><td>1 producer, 1 consumer</td>
 * </tr>
 * <tr>
 * <td>foo = "http | log", log.count=x</td><td>foo.0</td><td>x partitions</td><td>1 producer, x consumers with static group 'springXD', achieves queue semantics</td>
 * </tr>
 * <tr>
 * <td>foo = "http | log", log.count=x + XD partitioning</td><td>still 1 topic 'foo.0'</td><td>x partitions + use key computed by XD</td><td>1 producer, x consumers with static group 'springXD', achieves queue semantics</td>
 * </tr>
 * <tr>
 * <td>foo = "http | log", log.count=x, concurrency=y</td><td>foo.0</td><td>x*y partitions</td><td>1 producer, x XD consumers, each with y threads</td>
 * </tr>
 * <tr>
 * <td>foo = "http | log", log.count=0, x actual log containers</td><td>foo.0</td><td>10(configurable) partitions</td><td>1 producer, x XD consumers. Can't know the number of partitions beforehand, so decide a number that better be greater than number of containers</td>
 * </tr>
 * </table>
 *
 * @author Eric Bottard
 * @author Marius Bogoevici
 */
public class KafkaMessageBus extends MessageBusSupport {

	private final AtomicInteger correlationIdCounter = new AtomicInteger(new Random().nextInt());

	public static final int METADATA_VERIFICATION_TIMEOUT = 5000;

	public static final int METADATA_VERIFICATION_RETRY_ATTEMPTS = 10;

	public static final double METADATA_VERIFICATION_RETRY_BACKOFF_MULTIPLIER = 1.5;

	public static final int METADATA_VERIFICATION_RETRY_INITIAL_INTERVAL = 100;

	public static final int METADATA_VERIFICATION_MAX_INTERVAL = 1000;

	public static final String BATCHING_ENABLED = "batchingEnabled";

	public static final String BATCH_SIZE = "batchSize";

	public static final String BATCH_TIMEOUT = "batchTimeout";

	public static final String REPLICATION_FACTOR = "replicationFactor";

	public static final String CONCURRENCY = "concurrency";

	public static final String REQUIRED_ACKS = "requiredAcks";

	public static final String COMPRESSION_CODEC = "compressionCodec";

	private static final String DEFAULT_COMPRESSION_CODEC = "default";

	private static final int DEFAULT_REQUIRED_ACKS = 1;

	/**
	 * Used when writing directly to ZK. This is what Kafka expects.
	 */
	public final static ZkSerializer utf8Serializer = new ZkSerializer() {

		@Override
		public byte[] serialize(Object data) throws ZkMarshallingError {
			try {
				return ((String) data).getBytes("UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				throw new ZkMarshallingError(e);
			}
		}

		@Override
		public Object deserialize(byte[] bytes) throws ZkMarshallingError {
			try {
				return new String(bytes, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				throw new ZkMarshallingError(e);
			}
		}
	};

	protected static final Set<Object> PRODUCER_COMPRESSION_PROPERTIES = new HashSet<Object>(
			Arrays.asList(new String[] {
					KafkaMessageBus.COMPRESSION_CODEC,
			}));

	private static final String XD_REPLY_CHANNEL = "xdReplyChannel";

	/**
	 * The consumer group to use when achieving point to point semantics (that
	 * consumer group name is static and hence shared by all containers).
	 */
	private static final String POINT_TO_POINT_SEMANTICS_CONSUMER_GROUP = "springXD";

	/**
	 * The headers that will be propagated, by default.
	 */
	private static final String[] STANDARD_HEADERS = new String[] {
			IntegrationMessageHeaderAccessor.CORRELATION_ID,
			IntegrationMessageHeaderAccessor.SEQUENCE_SIZE,
			IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER,
			MessageHeaders.CONTENT_TYPE,
			ORIGINAL_CONTENT_TYPE_HEADER,
			XD_REPLY_CHANNEL
	};

	/**
	 * Basic + concurrency + partitioning.
	 */
	private static final Set<Object> SUPPORTED_CONSUMER_PROPERTIES = new SetBuilder()
			.addAll(CONSUMER_STANDARD_PROPERTIES)
			.add(BusProperties.PARTITION_INDEX) // Not actually used
			.add(BusProperties.COUNT) // Not actually used
			.add(BusProperties.CONCURRENCY)
			.build();

	/**
	 * Basic + concurrency.
	 */
	private static final Set<Object> SUPPORTED_NAMED_CONSUMER_PROPERTIES = new SetBuilder()
			.addAll(CONSUMER_STANDARD_PROPERTIES)
			.build();

	private static final Set<Object> SUPPORTED_NAMED_PRODUCER_PROPERTIES = new SetBuilder()
			.addAll(PRODUCER_STANDARD_PROPERTIES)
			.addAll(PRODUCER_BATCHING_BASIC_PROPERTIES)
			.build();

	/**
	 * Partitioning + kafka producer properties.
	 */
	private static final Set<Object> SUPPORTED_PRODUCER_PROPERTIES = new SetBuilder()
			.addAll(PRODUCER_PARTITIONING_PROPERTIES)
			.addAll(PRODUCER_STANDARD_PROPERTIES)
			.add(BusProperties.DIRECT_BINDING_ALLOWED)
			.addAll(PRODUCER_BATCHING_BASIC_PROPERTIES)
			.addAll(PRODUCER_COMPRESSION_PROPERTIES)
			.build();

	private final EmbeddedHeadersMessageConverter embeddedHeadersMessageConverter = new EmbeddedHeadersMessageConverter();

	private final ZookeeperConnect zookeeperConnect;

	private String brokers;

	private ExecutorService executor = Executors.newCachedThreadPool();

	private String[] headersToMap;

	private String zkAddress;

	// -------- Default values for properties -------
	private int defaultReplicationFactor = 1;

	private String defaultCompressionCodec = DEFAULT_COMPRESSION_CODEC;

	private int defaultRequiredAcks = DEFAULT_REQUIRED_ACKS;

	private ConnectionFactory connectionFactory;

	private String offsetStoreTopic = "SpringXdOffsets";


	public KafkaMessageBus(ZookeeperConnect zookeeperConnect, String brokers, String zkAddress,
			MultiTypeCodec<Object> codec, String... headersToMap) {
		this.zookeeperConnect = zookeeperConnect;
		this.brokers = brokers;
		this.zkAddress = zkAddress;
		setCodec(codec);
		if (headersToMap.length > 0) {
			String[] combinedHeadersToMap =
					Arrays.copyOfRange(STANDARD_HEADERS, 0, STANDARD_HEADERS.length + headersToMap.length);
			System.arraycopy(headersToMap, 0, combinedHeadersToMap, STANDARD_HEADERS.length, headersToMap.length);
			this.headersToMap = combinedHeadersToMap;
		}
		else {
			this.headersToMap = STANDARD_HEADERS;
		}

	}

	public void setOffsetStoreTopic(String offsetStoreTopic) {
		this.offsetStoreTopic = offsetStoreTopic;
	}

	public ConnectionFactory getConnectionFactory() {
		return connectionFactory;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// we instantiate the connection factory here due to https://jira.spring.io/browse/XD-2647
		DefaultConnectionFactory defaultConnectionFactory =
				new DefaultConnectionFactory(new ZookeeperConfiguration(this.zookeeperConnect));
		defaultConnectionFactory.afterPropertiesSet();
		this.connectionFactory = defaultConnectionFactory;

	}

	/**
	 * Allowed chars are ASCII alphanumerics, '.', '_' and '-'.
	 * '_' is used as escaped char in the form '_xx' where xx is the hexadecimal
	 * value of the byte(s) needed to represent an illegal char in utf8.
	 */
	/*default*/
	public static String escapeTopicName(String original) {
		StringBuilder result = new StringBuilder(original.length());
		try {
			byte[] utf8 = original.getBytes("UTF-8");
			for (byte b : utf8) {
				if ((b >= 'a') && (b <= 'z') || (b >= 'A') && (b <= 'Z') || (b >= '0') && (b <= '9') || (b == '.')
						|| (b == '-')) {
					result.append((char) b);
				}
				else {
					result.append(String.format("_%02X", b));
				}
			}
		}
		catch (UnsupportedEncodingException e) {
			throw new AssertionError(e); // Can't happen
		}
		return result.toString();
	}

	public void setDefaultReplicationFactor(int defaultReplicationFactor) {
		this.defaultReplicationFactor = defaultReplicationFactor;
	}

	public void setDefaultCompressionCodec(String defaultCompressionCodec) {
		this.defaultCompressionCodec = defaultCompressionCodec;
	}

	public void setDefaultRequiredAcks(int defaultRequiredAcks) {
		this.defaultRequiredAcks = defaultRequiredAcks;
	}

	@Override
	public void bindConsumer(String name, final MessageChannel moduleInputChannel, Properties properties) {
		// Point-to-point consumers reset at the earliest time, which allows them to catch up with all messages
		createKafkaConsumer(name, moduleInputChannel, properties, POINT_TO_POINT_SEMANTICS_CONSUMER_GROUP,
				OffsetRequest.EarliestTime());
		bindExistingProducerDirectlyIfPossible(name, moduleInputChannel);
	}

	@Override
	public void bindPubSubConsumer(String name, MessageChannel inputChannel, Properties properties) {
		// Usage of a different consumer group each time achieves pub-sub
		// PubSub consumers reset at the latest time, which allows them to receive only messages sent after
		// they've been bound
		String group = UUID.randomUUID().toString();
		createKafkaConsumer(name, inputChannel, properties, group, OffsetRequest.LatestTime());
	}

	@Override
	public void bindProducer(final String name, MessageChannel moduleOutputChannel, Properties properties) {

		Assert.isInstanceOf(SubscribableChannel.class, moduleOutputChannel);
		KafkaPropertiesAccessor accessor = new KafkaPropertiesAccessor(properties);
		if (name.startsWith(P2P_NAMED_CHANNEL_TYPE_PREFIX)) {
			validateProducerProperties(name, properties, SUPPORTED_NAMED_PRODUCER_PROPERTIES);
		}
		else {
			validateProducerProperties(name, properties, SUPPORTED_PRODUCER_PROPERTIES);
		}
		if (!bindNewProducerDirectlyIfPossible(name, (SubscribableChannel) moduleOutputChannel, accessor)) {
			if (logger.isInfoEnabled()) {
				logger.info("Using kafka topic for outbound: " + name);
			}

			final String topicName = escapeTopicName(name);
			int numPartitions = accessor.getNumberOfKafkaPartitionsForProducer();

			TopicMetadata targetTopicMetadata = ensureTopicCreated(topicName, numPartitions, defaultReplicationFactor);

			MessageHandler messageHandler = createTopicProducerHandler(accessor, topicName);

			MessageHandler handler = new SendingHandler(messageHandler, topicName, null, accessor,
					targetTopicMetadata.partitionsMetadata().size(), null);
			EventDrivenConsumer consumer = new EventDrivenConsumer((SubscribableChannel) moduleOutputChannel, handler);
			consumer.setBeanFactory(this.getBeanFactory());
			consumer.setBeanName("outbound." + name);
			consumer.afterPropertiesSet();

			Binding producerBinding = Binding.forProducer(name, moduleOutputChannel, consumer, accessor);
			addBinding(producerBinding);
			producerBinding.start();
		}

	}

	private MessageHandler createTopicProducerHandler(KafkaPropertiesAccessor accessor, final String topicName) {
		ProducerMetadata<Integer, byte[]> producerMetadata = new ProducerMetadata<Integer, byte[]>(
				topicName);
		producerMetadata.setValueEncoder(new DefaultEncoder(null));
		producerMetadata.setValueClassType(byte[].class);
		producerMetadata.setKeyEncoder(new IntegerEncoderDecoder(null));
		producerMetadata.setKeyClassType(Integer.class);
		producerMetadata.setCompressionCodec(accessor.getCompressionCodec(this.defaultCompressionCodec));
		producerMetadata.setPartitioner(new DefaultPartitioner(null));

		Properties additionalProps = new Properties();
		additionalProps.put("request.required.acks", String.valueOf(accessor.getRequiredAcks(this.defaultRequiredAcks)));
		if (accessor.isBatchingEnabled(this.defaultBatchingEnabled)) {
			producerMetadata.setAsync(true);
			producerMetadata.setBatchNumMessages(String.valueOf(accessor.getBatchSize(this.defaultBatchSize)));
			additionalProps.put("queue.buffering.max.ms", String.valueOf(accessor.getBatchTimeout(this.defaultBatchTimeout)));
		}

		ProducerFactoryBean<Integer, byte[]> producerFB = new ProducerFactoryBean<Integer, byte[]>(producerMetadata, brokers, additionalProps);

		final Producer<Integer, byte[]> producer;
		try {
			producer = producerFB.getObject();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		final ProducerConfiguration<Integer, byte[]> producerConfiguration = new ProducerConfiguration<Integer, byte[]>(
				producerMetadata, producer);

		return new AbstractMessageHandler() {
			@Override
			protected void handleMessageInternal(Message<?> message) throws Exception {
				producerConfiguration.send(topicName, message.getHeaders().get("messageKey"), message);
			}
		};
	}

	@Override
	public void bindPubSubProducer(String name, MessageChannel outputChannel, Properties properties) {
		bindProducer(name, outputChannel, properties);
	}

	@Override
	public void bindRequestor(String name, MessageChannel requests, MessageChannel replies, Properties properties) {
		if (logger.isInfoEnabled()) {
			logger.info("binding requestor: " + name);
		}
		Assert.isInstanceOf(SubscribableChannel.class, requests);
		validateProducerProperties(name, properties, PRODUCER_STANDARD_PROPERTIES);
		KafkaPropertiesAccessor accessor = new KafkaPropertiesAccessor(properties);

		// create producer
		String requestsTopicName = escapeTopicName("queue." + name + ".requests");
		ensureTopicCreated(requestsTopicName, 1, 1);
		MessageHandler sendingMessageHandler = createTopicProducerHandler(accessor, requestsTopicName);

		String replyQueueName = name + ".replies." + this.getIdGenerator().generateId();
		ensureTopicCreated(escapeTopicName(replyQueueName), 1, defaultReplicationFactor);
		Assert.isInstanceOf(SubscribableChannel.class, requests);

		MessageHandler handler = new SendingHandler(sendingMessageHandler, requestsTopicName
				, replyQueueName, accessor, 1, null);
		EventDrivenConsumer consumer = new EventDrivenConsumer((SubscribableChannel) requests, handler);
		consumer.setBeanFactory(this.getBeanFactory());
		consumer.setBeanName("outbound." + name);
		consumer.afterPropertiesSet();
		Binding producerBinding = Binding.forProducer(name, requests, consumer, accessor);
		addBinding(producerBinding);
		producerBinding.start();

		// create consumer
		KafkaMessageDrivenChannelAdapter adapter = new KafkaMessageDrivenChannelAdapter(createMessageListenerContainer
				(POINT_TO_POINT_SEMANTICS_CONSUMER_GROUP, accessor.getConcurrency(1),
						connectionFactory.getPartitions(escapeTopicName("queue." + name + "" + ".requests")), OffsetRequest.EarliestTime()));
		DirectChannel bridgeToModuleChannel = new DirectChannel();
		bridgeToModuleChannel.setBeanFactory(this.getBeanFactory());
		bridgeToModuleChannel.setBeanName(name + ".bridge");
		adapter.setOutputChannel(bridgeToModuleChannel);
		adapter.setBeanName("inbound." + name);
		adapter.setBeanFactory(getBeanFactory());
		adapter.afterPropertiesSet();
		adapter.start();
		Binding consumerBinding = Binding.forConsumer(name, adapter, replies, accessor);
		addBinding(consumerBinding);
		ReceivingHandler convertingBridge = new ReceivingHandler(adapter);
		convertingBridge.setOutputChannel(replies);
		convertingBridge.setBeanName(name + ".bridge.handler");
		convertingBridge.afterPropertiesSet();
		bridgeToModuleChannel.subscribe(convertingBridge);
		consumerBinding.start();
	}

	@Override
	public void bindReplier(String name, MessageChannel requests, MessageChannel replies, Properties properties) {
		if (logger.isInfoEnabled()) {
			logger.info("binding replier: " + name);
		}
		validateConsumerProperties(name, properties, CONSUMER_STANDARD_PROPERTIES);
		KafkaPropertiesAccessor accessor = new KafkaPropertiesAccessor(properties);
		String requestsTopicName = escapeTopicName("queue." + name + "" + ".requests");
		ensureTopicCreated(requestsTopicName, 1, defaultReplicationFactor);
		KafkaMessageDrivenChannelAdapter adapter = new KafkaMessageDrivenChannelAdapter(createMessageListenerContainer
				(POINT_TO_POINT_SEMANTICS_CONSUMER_GROUP, accessor.getConcurrency(1),
						connectionFactory.getPartitions(requestsTopicName), OffsetRequest.EarliestTime()));
		DirectChannel bridgeToModuleChannel = new DirectChannel();
		bridgeToModuleChannel.setBeanFactory(this.getBeanFactory());
		bridgeToModuleChannel.setBeanName(name + ".bridge");
		adapter.setOutputChannel(bridgeToModuleChannel);
		adapter.setBeanName("inbound." + name);
		adapter.setBeanFactory(getBeanFactory());
		adapter.afterPropertiesSet();
		adapter.start();
		Binding consumerBinding = Binding.forConsumer(name, adapter, requests, accessor);
		addBinding(consumerBinding);
		ReceivingHandler convertingBridge = new ReceivingHandler(adapter);
		convertingBridge.setOutputChannel(requests);
		convertingBridge.setBeanName(name + ".bridge.handler");
		convertingBridge.afterPropertiesSet();
		bridgeToModuleChannel.subscribe(convertingBridge);
		consumerBinding.start();


		MessageHandler sendingMessageHandler = createTopicProducerHandler(accessor, requestsTopicName);
		String replyQueueName = name + ".replies." + this.getIdGenerator().generateId();
		Assert.isInstanceOf(SubscribableChannel.class, requests);

		MessageHandler handler = new SendingHandler(sendingMessageHandler, null, replyQueueName, accessor, 1, parser
				.parseExpression("headers['" + REPLY_TO + "']"));
		EventDrivenConsumer consumer = new EventDrivenConsumer((SubscribableChannel) requests, handler);
		consumer.setBeanFactory(this.getBeanFactory());
		consumer.setBeanName("outbound." + name);
		consumer.afterPropertiesSet();
		Binding producerBinding = Binding.forProducer(name, replies, consumer, accessor);
		addBinding(producerBinding);
		producerBinding.start();
	}

	/**
	 * Creates a Kafka topic if needed, or try to increase its partition count to the desired number.
	 */
	private TopicMetadata ensureTopicCreated(final String topicName, final int numPartitions, int replicationFactor) {

		final int sessionTimeoutMs = 10000;
		final int connectionTimeoutMs = 10000;
		final ZkClient zkClient = new ZkClient(zkAddress, sessionTimeoutMs, connectionTimeoutMs, utf8Serializer);
		try {
			// The following is basically copy/paste from AdminUtils.createTopic() with
			// createOrUpdateTopicPartitionAssignmentPathInZK(..., update=true)
			Properties topicConfig = new Properties();
			Seq<Object> brokerList = ZkUtils.getSortedBrokerList(zkClient);
			scala.collection.Map<Object, Seq<Object>> replicaAssignment = AdminUtils.assignReplicasToBrokers(brokerList,
					numPartitions, replicationFactor, -1, -1);
			AdminUtils.createOrUpdateTopicPartitionAssignmentPathInZK(zkClient, topicName, replicaAssignment, topicConfig,
					true);

			RetryTemplate retryTemplate = new RetryTemplate();

			CompositeRetryPolicy policy = new CompositeRetryPolicy();
			TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
			timeoutRetryPolicy.setTimeout(METADATA_VERIFICATION_TIMEOUT);
			SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
			simpleRetryPolicy.setMaxAttempts(METADATA_VERIFICATION_RETRY_ATTEMPTS);
			policy.setPolicies(new RetryPolicy[] {timeoutRetryPolicy, simpleRetryPolicy});
			retryTemplate.setRetryPolicy(policy);

			ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
			backOffPolicy.setInitialInterval(METADATA_VERIFICATION_RETRY_INITIAL_INTERVAL);
			backOffPolicy.setMultiplier(METADATA_VERIFICATION_RETRY_BACKOFF_MULTIPLIER);
			backOffPolicy.setMaxInterval(METADATA_VERIFICATION_MAX_INTERVAL);
			retryTemplate.setBackOffPolicy(backOffPolicy);

			try {
				TopicMetadata topicMetadata = retryTemplate.execute(new RetryCallback<TopicMetadata, Exception>() {
					@Override
					public TopicMetadata doWithRetry(RetryContext context) throws Exception {
						TopicMetadata topicMetadata = AdminUtils.fetchTopicMetadataFromZk(topicName, zkClient);
						if (topicMetadata.errorCode() != ErrorMapping.NoError() || !topicName.equals(topicMetadata.topic())) {
							// downcast to Exception because that's what the error throws
							throw (Exception) ErrorMapping.exceptionFor(topicMetadata.errorCode());
						}
						List<PartitionMetadata> partitionMetadatas = new kafka.javaapi.TopicMetadata(topicMetadata).partitionsMetadata();
						if (partitionMetadatas.size() != numPartitions) {
							throw new IllegalStateException("The number of expected partitions was: " + numPartitions + ", but " +
									partitionMetadatas.size() + " have been found instead");
						}
						for (PartitionMetadata partitionMetadata : partitionMetadatas) {
							if (partitionMetadata.errorCode() != ErrorMapping.NoError()) {
								throw (Exception) ErrorMapping.exceptionFor(partitionMetadata.errorCode());
							}
						}
						return topicMetadata;
					}
				});
				// work around an issue in Spring
				this.connectionFactory.refreshLeaders(Collections.<String>emptySet());

				return topicMetadata;
			}
			catch (Exception e) {
				logger.error("Cannot initialize MessageBus", e);
				throw new RuntimeException("Cannot initialize message bus:", e);
			}

		}
		finally {
			zkClient.close();
		}
	}

	private void createKafkaConsumer(String name, final MessageChannel moduleInputChannel, Properties properties,
			String group, long referencePoint) {

		if (name.startsWith(P2P_NAMED_CHANNEL_TYPE_PREFIX)) {
			validateConsumerProperties(name, properties, SUPPORTED_NAMED_CONSUMER_PROPERTIES);
		}
		else {
			validateConsumerProperties(name, properties, SUPPORTED_CONSUMER_PROPERTIES);
		}
		KafkaPropertiesAccessor accessor = new KafkaPropertiesAccessor(properties);

		int concurrency = accessor.getConcurrency(defaultConcurrency);
		String topic = escapeTopicName(name);

		ensureTopicCreated(topic, accessor.getCount() * concurrency, defaultReplicationFactor);

		Collection<Partition> allPartitions = connectionFactory.getPartitions(topic);

		Collection<Partition> listenedPartitions;

		int moduleCount = accessor.getCount();

		if (moduleCount == 1) {
			listenedPartitions = allPartitions;
		}
		else {
			listenedPartitions = new ArrayList<Partition>();
			for (Partition partition : allPartitions) {
				// divide partitions across modules
				if (accessor.getPartitionIndex() != -1) {
					if ((partition.getId() % moduleCount) == accessor.getPartitionIndex()) {
						listenedPartitions.add(partition);
					}
				}
				else {
					int moduleSequence = accessor.getSequence();
					if (moduleCount == 0) {
						throw new IllegalArgumentException("This type of transport does not support 0-count modules");
					}
					if (moduleCount == 1) {
						listenedPartitions.add(partition);
					}
					else {
						// sequence numbers are zero-based
						if ((partition.getId() % moduleCount) == (moduleSequence - 1)) {
							listenedPartitions.add(partition);
						}
					}
				}
			}
		}


		KafkaMessageListenerContainer messageListenerContainer =
				createMessageListenerContainer(group, concurrency, listenedPartitions,
						referencePoint);

		final DirectChannel bridge = new DirectChannel();

		Decoder<byte[]> valueDecoder = new DefaultDecoder(null);
		Decoder<Integer> keyDecoder = new IntegerEncoderDecoder();
		KafkaMessageDrivenChannelAdapter kafkaMessageDrivenChannelAdapter =
				new KafkaMessageDrivenChannelAdapter(messageListenerContainer);
		kafkaMessageDrivenChannelAdapter.setBeanFactory(this.getBeanFactory());
		kafkaMessageDrivenChannelAdapter.setKeyDecoder(keyDecoder);
		kafkaMessageDrivenChannelAdapter.setPayloadDecoder(valueDecoder);
		kafkaMessageDrivenChannelAdapter.setOutputChannel(bridge);
		kafkaMessageDrivenChannelAdapter.afterPropertiesSet();
		kafkaMessageDrivenChannelAdapter.start();

		ReceivingHandler rh = new ReceivingHandler(kafkaMessageDrivenChannelAdapter);
		rh.setOutputChannel(moduleInputChannel);
		EventDrivenConsumer edc = new EventDrivenConsumer(bridge, rh);
		edc.setBeanName("inbound." + name);

		Binding consumerBinding = Binding.forConsumer(name, edc, moduleInputChannel, accessor);
		addBinding(consumerBinding);
		consumerBinding.start();

	}

//	public KafkaMessageListenerContainer createMessageListenerContainer(String group, int numThreads, String topic,
//			long referencePoint) {
//		return createMessageListenerContainer(group, numThreads, topic, null, referencePoint);
//	}
//
//	public KafkaMessageListenerContainer createMessageListenerContainer(String group, int numThreads,
//			Collection<Partition> listenedPartitions, long referencePoint) {
//		return createMessageListenerContainer(group, numThreads, null, listenedPartitions, referencePoint);
//	}

	public KafkaMessageListenerContainer createMessageListenerContainer(String group, int numThreads,
			Collection<Partition> listenedPartitions, long referencePoint) {
		KafkaMessageListenerContainer messageListenerContainer;
		messageListenerContainer = new KafkaMessageListenerContainer(connectionFactory,
				listenedPartitions.toArray(new Partition[listenedPartitions.size()]));

		// if we have less target partitions than target concurrency, adjust accordingly
		messageListenerContainer.setConcurrency(Math.min(numThreads, listenedPartitions.size()));
		KafkaTopicMetadataStore springXDOffsets =
				new KafkaTopicMetadataStore(zookeeperConnect, connectionFactory, offsetStoreTopic);
		try {
			springXDOffsets.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		MetadataStoreOffsetManager offsetManager = new MetadataStoreOffsetManager(connectionFactory);
		offsetManager.setMetadataStore(springXDOffsets);
		offsetManager.setConsumerId(group);
		offsetManager.setReferenceTimestamp(referencePoint);
		messageListenerContainer.setOffsetManager(offsetManager);
		return messageListenerContainer;
	}

	private class KafkaPropertiesAccessor extends AbstractBusPropertiesAccessor {

		private final String[] KAFKA_PROPERTIES = new String[] {
				BATCHING_ENABLED,
				BATCH_SIZE,
				BATCH_TIMEOUT,
				CONCURRENCY,
				REPLICATION_FACTOR,
				COMPRESSION_CODEC,
				REQUIRED_ACKS
		};

		public KafkaPropertiesAccessor(Properties properties) {
			super(properties);
		}

		public int getNumberOfKafkaPartitionsForProducer() {
			int concurrency = getProperty(NEXT_MODULE_CONCURRENCY, defaultConcurrency);
			if (new PartitioningMetadata(this).isPartitionedModule()) {
				return getPartitionCount() * concurrency;
			}
			else {
				int nextModuleCount = getProperty(NEXT_MODULE_COUNT, 1);
				if (nextModuleCount == 0) {
					throw new IllegalArgumentException("Module count cannot be zero");
				}
				return nextModuleCount * concurrency;
			}
		}

		public String getCompressionCodec(String defaultValue) {
			return getProperty(COMPRESSION_CODEC, defaultValue);
		}

		public int getRequiredAcks(int defaultRequiredAcks) {
			return getProperty(REQUIRED_ACKS, defaultRequiredAcks);
		}

		public String[] getDefaultProperties() {
			return KAFKA_PROPERTIES;
		}
	}

	public String[] getMessageBusSpecificProperties() {
		KafkaPropertiesAccessor propertiesAccessor = new KafkaPropertiesAccessor(null);
		return propertiesAccessor.getDefaultProperties();
	}

	private class ReceivingHandler extends AbstractReplyProducingMessageHandler implements Lifecycle {

		private KafkaMessageDrivenChannelAdapter kafkaMessageDrivenChannelAdapter;

		public ReceivingHandler(KafkaMessageDrivenChannelAdapter kafkaMessageDrivenChannelAdapter) {
			this.kafkaMessageDrivenChannelAdapter = kafkaMessageDrivenChannelAdapter;
			this.setBeanFactory(KafkaMessageBus.this.getBeanFactory());
		}

		@Override
		@SuppressWarnings("unchecked")
		protected Object handleRequestMessage(Message<?> requestMessage) {
			Message<?> theRequestMessage = requestMessage;
			try {
				theRequestMessage = embeddedHeadersMessageConverter.extractHeaders((Message<byte[]>) requestMessage);
				System.out.println("Received:");
				for (Map.Entry<String,Object> o : theRequestMessage.getHeaders().entrySet()	) {
					System.out.println(o.getKey() + "=" + o.getValue());
				}
			}
			catch (UnsupportedEncodingException e) {
				logger.error("Could not convert message", e);
			}
			return deserializePayloadIfNecessary(theRequestMessage);
		}

		@Override
		public void start() {
		}

		@Override
		public void stop() {
			kafkaMessageDrivenChannelAdapter.stop();
		}

		@Override
		public boolean isRunning() {
			return true;
		}

	}

	private class SendingHandler extends AbstractMessageHandler {

		private final MessageHandler delegate;

		private final PartitioningMetadata partitioningMetadata;

		private final AtomicInteger roundRobinCount = new AtomicInteger();

		private final String topicName;

		private final Expression expression;

		private final int numberOfKafkaPartitions;

		private int factor;

		private final String replyTo;


		private SendingHandler(MessageHandler delegate, String topicName, String replyTo,
				KafkaPropertiesAccessor properties, int numberOfPartitions, Expression expression) {
			this.replyTo = replyTo;
			Assert.isTrue(StringUtils.hasText(topicName) ^ expression != null, "Either a topic name or an expression must " +
					"be provided");
			this.delegate = delegate;
			this.topicName = topicName;
			this.numberOfKafkaPartitions = numberOfPartitions;
			this.expression = expression;
			this.partitioningMetadata = new PartitioningMetadata(properties);
			this.setBeanFactory(KafkaMessageBus.this.getBeanFactory());
			if (partitioningMetadata.isPartitionedModule()) {
				// ceiling division of total number of kafka partitions to the Spring XD partition count
				// a container with a certain partition index will listen to at most this number of
				// partitions, evenly distributed. E.g. for 8 kafka partitions and 3 partition indices
				// the factor is 3, partition 0 will listen to 0,3,6, etc.
				// this value will be used further fanning data out to the multiple Kafka partitions that
				// are assigned to a given partition index
				this.factor = (numberOfKafkaPartitions + partitioningMetadata.getPartitionCount() - 1)
						/ partitioningMetadata.getPartitionCount();
			}
		}

		@Override
		protected void handleMessageInternal(Message<?> message) throws Exception {
			Map<String, Object> additionalHeaders = new HashMap<String, Object>();

			int partition;
			if (partitioningMetadata.isPartitionedModule()) {
				// this is the logical partition in Spring XD - we need to fan out the messages further
				int springXdPartition = determinePartition(message, partitioningMetadata);
				// in order to do so, we will generate a number whose modulus when applied to total partition count
				// is equal to springXdPartition
				partition = (roundRobin() % factor) * partitioningMetadata.getPartitionCount() + springXdPartition;
			}
			else {
				// The value will be modulo-ed by numPartitions by Kafka itself
				partition = roundRobin();
			}
			additionalHeaders.put(PARTITION_HEADER, partition);
			additionalHeaders.put("messageKey", partition);
			if (expression != null) {
				additionalHeaders.put("topic", expression.getValue(evaluationContext, message, String.class));
			}
			else {
				additionalHeaders.put("topic", topicName);
			}
			if (replyTo != null) {
				additionalHeaders.put(REPLY_TO, this.replyTo);
			}

			System.out.println("Sending with headers:");

			for (Map.Entry<String, Object> stringObjectEntry : additionalHeaders.entrySet()) {
					System.out.println(stringObjectEntry.getKey() + "=" + stringObjectEntry.getValue());
			}

			@SuppressWarnings("unchecked")
			Message<byte[]> transformed = (Message<byte[]>) serializePayloadIfNecessary(message,
					MimeTypeUtils.APPLICATION_OCTET_STREAM);
			transformed = getMessageBuilderFactory().fromMessage(transformed)
					.copyHeaders(additionalHeaders)
					.build();
			Message<?> messageToSend = embeddedHeadersMessageConverter.embedHeaders(transformed,
					KafkaMessageBus.this.headersToMap);
			Assert.isInstanceOf(byte[].class, messageToSend.getPayload());
			delegate.handleMessage(messageToSend);
		}

		private int roundRobin() {
			int result = roundRobinCount.incrementAndGet();
			if (result == Integer.MAX_VALUE) {
				roundRobinCount.set(0);
			}
			return result;
		}

	}

}
