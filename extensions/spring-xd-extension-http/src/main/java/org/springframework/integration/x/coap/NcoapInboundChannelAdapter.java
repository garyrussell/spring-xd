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

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.netty.buffer.ChannelBuffer;

import org.springframework.integration.endpoint.MessageProducerSupport;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.SettableFuture;

import de.uniluebeck.itm.ncoap.application.server.CoapServerApplication;
import de.uniluebeck.itm.ncoap.application.server.webservice.NotObservableWebservice;
import de.uniluebeck.itm.ncoap.application.server.webservice.linkformat.LongLinkAttribute;
import de.uniluebeck.itm.ncoap.message.CoapMessage;
import de.uniluebeck.itm.ncoap.message.CoapRequest;
import de.uniluebeck.itm.ncoap.message.CoapResponse;
import de.uniluebeck.itm.ncoap.message.MessageCode;
import de.uniluebeck.itm.ncoap.message.options.ContentFormat;


/**
 *
 * @author Gary Russell
 */
public class NcoapInboundChannelAdapter extends MessageProducerSupport {

	private final CoapServerApplication server = new CoapServerApplication();

	private volatile XDNotObservableWebservice service;

	private final String servicePath;

	public NcoapInboundChannelAdapter() {
		this("/xd");
	}

	public NcoapInboundChannelAdapter(String servicePath) {
		this.servicePath = servicePath;
	}

	@Override
	protected void doStart() {
		this.service = new XDNotObservableWebservice(servicePath, "", 5000, server.getExecutor());
		this.server.registerService(this.service);
	}

	@Override
	protected void doStop() {
		this.service.shutdown();
		this.server.shutdown();
	}

	private class XDNotObservableWebservice extends NotObservableWebservice<String> {

		private int weakEtag;

		protected XDNotObservableWebservice(String servicePath, String initialStatus, long lifetimeSeconds,
				ScheduledExecutorService executor) {
			super(servicePath, initialStatus, lifetimeSeconds, executor);

			this.setLinkAttribute(new LongLinkAttribute(LongLinkAttribute.CONTENT_TYPE, ContentFormat.TEXT_PLAIN_UTF8));
			this.setLinkAttribute(new LongLinkAttribute(LongLinkAttribute.CONTENT_TYPE, ContentFormat.APP_XML));
		}


		@Override
		public byte[] getEtag(long contentFormat) {
			return Ints.toByteArray(weakEtag & Longs.hashCode(contentFormat));
		}


		@Override
		public void updateEtag(String resourceStatus) {
			weakEtag = resourceStatus.hashCode();
		}


		@Override
		public void shutdown() {
			//nothing to to
		}

		@Override
		public void processCoapRequest(SettableFuture<CoapResponse> responseFuture, CoapRequest coapRequest,
				InetSocketAddress remoteEndpoint) throws Exception {

			if (coapRequest.getMessageCodeName() != MessageCode.Name.POST)
				setMethodNotAllowedResponse(responseFuture, coapRequest);
			else
				processCoapPostRequest(responseFuture, coapRequest, remoteEndpoint);

		}


		public void processCoapPostRequest(SettableFuture<CoapResponse> responseFuture, CoapRequest coapRequest,
				InetSocketAddress remoteEndpoint) {
			ChannelBuffer content = coapRequest.getContent();
			String payload = content.toString(Charset.defaultCharset());
			sendMessage(getMessageBuilderFactory()
					.withPayload(payload)
					.setHeader("coap_options", coapRequest.getAllOptions())
					.setHeader("coap_method", coapRequest.getMessageCodeName())
					.setHeader("coap_token", coapRequest.getToken())
					.setHeader("coap_remoteIp", remoteEndpoint.getAddress())
					.setHeader("coap_remotePort", remoteEndpoint.getPort())
					.build());
			//create CoAP response
			CoapResponse coapResponse = new CoapResponse(coapRequest.getMessageTypeName(), MessageCode.Name.CREATED_201);
			responseFuture.set(coapResponse);
		}


		public void setMethodNotAllowedResponse(SettableFuture<CoapResponse> responseFuture, CoapRequest coapRequest)
				throws Exception {

			CoapResponse coapResponse = new CoapResponse(coapRequest.getMessageTypeName(),
					MessageCode.Name.METHOD_NOT_ALLOWED_405);

			coapResponse.setContent("Only method POST is allowed!".getBytes(CoapMessage.CHARSET),
					ContentFormat.TEXT_PLAIN_UTF8);

			responseFuture.set(coapResponse);
		}

		@Override
		public byte[] getSerializedResourceStatus(long contentFormat) {
			String result = null;
			if (contentFormat == ContentFormat.TEXT_PLAIN_UTF8)
				result = "The resource status is " + getStatus() + ".";

			else if (contentFormat == ContentFormat.APP_XML)
				result = "<status>" + getStatus() + "</status>";


			if (result == null)
				return null;

			else
				return result.getBytes(CoapMessage.CHARSET);
		}
	}

}
