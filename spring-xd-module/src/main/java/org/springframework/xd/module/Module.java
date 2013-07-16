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

package org.springframework.xd.module;

import java.util.List;
import java.util.Properties;

import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * @author Mark Fisher
 * @author David Turanski
 * @author Gary Russell
 */
public interface Module extends Lifecycle {

	/**
	 * @return the generic module name or template name
	 */
	String getName();

	String getType();

	DeploymentMetadata getDeploymentMetadata();

	void setParentContext(ApplicationContext parentContext);

	void addComponents(Resource resource);

	ApplicationContext getApplicationContext();

	void addProperties(Properties properties);

	List<MediaType> getAcceptedMediaTypes();

	Properties getProperties();

}
