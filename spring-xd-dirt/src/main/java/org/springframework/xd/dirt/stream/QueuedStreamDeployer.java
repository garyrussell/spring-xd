/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.dirt.stream;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.util.Assert;
import org.springframework.xd.dirt.module.ModuleDeploymentRequest;
import org.springframework.xd.dirt.module.ModuleDeploymentRequestQueue;

/**
 * A {@link StreamDeployer} implementation that uses a {@link ModuleDeploymentRequestQueue}
 * @author Mark Fisher
 * @author Gary Russell
 * @author David Turanski
 */
public class QueuedStreamDeployer extends StreamDeployerSupport implements StreamDeployer {

	private final StreamParser streamParser;
	private final ModuleDeploymentRequestQueue moduleDeploymentRequestQueue;

	public QueuedStreamDeployer(StreamParser streamParser, ModuleDeploymentRequestQueue moduleDeploymentRequestQueue) {
		Assert.notNull(streamParser, "streamParser cannot be null");
		Assert.notNull(moduleDeploymentRequestQueue, "moduleDeploymentRequestQueue cannot be null");
		this.streamParser = streamParser;
		this.moduleDeploymentRequestQueue = moduleDeploymentRequestQueue;
	}

	@Override
	public void deployStream(String name, String config) {
		List<ModuleDeploymentRequest> requests = this.streamParser.parse(name, config);
		this.addDeployment(name, requests);
		for (ModuleDeploymentRequest request : requests) {
			this.moduleDeploymentRequestQueue.addRequest(request);
		}
	}

	@Override
	public void undeployStream(String name) {
		List<ModuleDeploymentRequest> requests = this.removeDeployment(name);
		if (requests != null) {
			// undeploy in the reverse sequence (source first)
			Collections.reverse(requests);
			for (ModuleDeploymentRequest request : requests) {
				request.setRemove(true);
				this.moduleDeploymentRequestQueue.addRequest(request);
			}
		}
	}
}
