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

package org.springframework.xd.dirt.plugins.stream;

import static org.springframework.xd.module.ModuleType.PROCESSOR;
import static org.springframework.xd.module.ModuleType.SINK;
import static org.springframework.xd.module.ModuleType.SOURCE;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.integration.MessageChannel;
import org.springframework.integration.x.channel.registry.ChannelRegistry;
import org.springframework.xd.dirt.container.DefaultContainer;
import org.springframework.xd.module.AbstractPlugin;
import org.springframework.xd.module.Module;

/**
 * @author Mark Fisher
 * @author Gary Russell
 * @author David Turanski
 * @author Jennifer Hickey
 * @author Glenn Renfro
 */
public class StreamPlugin extends AbstractPlugin {

	private static final String CONTEXT_CONFIG_ROOT = DefaultContainer.XD_CONFIG_ROOT
			+ "plugins/stream/";

	private static final String TAP_XML = CONTEXT_CONFIG_ROOT + "tap.xml";

	private static final String CHANNEL_REGISTRY = CONTEXT_CONFIG_ROOT + "channel-registry.xml";

	public StreamPlugin(){
		postProcessContextPath = CHANNEL_REGISTRY;
	}
	private static final String TAP = "tap";

	@Override
	public List<String>  componentPathsSelector(Module module) {
		ArrayList<String> result = new ArrayList<String>();
		String type = module.getType();
		// This doesn't seem right - shouldn't the channel registry be in the parent context?
		// here temporarily to make StreamPluginTests Run - probably need to add a parent context to those tests:
		if ((SOURCE.equals(type) || PROCESSOR.equals(type) || SINK.equals(type)) && module.getDeploymentMetadata().getGroup() != null) {
			result.add(CHANNEL_REGISTRY);
		}
		if (TAP.equals(module.getName()) && SOURCE.equals(type)) {
			result.add(TAP_XML);
		}
		return result;
	}

	@Override
	public void configureProperties(Module module) {
		String type = module.getType();
		String group = module.getDeploymentMetadata().getGroup();
		if ((SOURCE.equals(type) || PROCESSOR.equals(type) || SINK.equals(type))
				&& group != null) {
			Properties properties = new Properties();
			properties.setProperty("xd.stream.name", group);
			properties.setProperty("xd.module.index", String.valueOf(module.getDeploymentMetadata().getIndex()));
			module.addProperties(properties);
		}
	}

	@Override
	protected void postProcessModuleInternal(Module module) {
		ChannelRegistry registry = findRegistry(module);
		if (registry != null) {
			MessageChannel channel = module.getComponent("input", MessageChannel.class);
			if (channel != null) {
				registry.inbound(module.getGroup() + "." + (module.getIndex() - 1), channel);
			}
			channel = module.getComponent("output", MessageChannel.class);
			if (channel != null) {
				registry.inbound(module.getGroup() + "." + module.getIndex(), channel);
			}
		}
	}

	private ChannelRegistry findRegistry(Module module) {
		ChannelRegistry registry = null;
		try {
			registry = module.getComponent(ChannelRegistry.class);
		}
		catch (Exception e) {
			// TODO - no registry
		}
		return registry;
	}

	@Override
	public void removeModule(Module module) {
		ChannelRegistry registry = findRegistry(module);
		if (registry != null) {
			registry.cleanAll(module.getGroup());
		}
	}


}
