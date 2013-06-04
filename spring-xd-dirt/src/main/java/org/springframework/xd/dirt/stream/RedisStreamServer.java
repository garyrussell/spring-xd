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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.integration.MessagingException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.xd.dirt.redis.ExceptionWrappingLettuceConnectionFactory;

/**
 * This is a temporary "server" for the REST API. Currently it only handles simple
 * stream configurations (tokens separated by pipes) without any parameters. This
 * will be completely replaced by a more robust solution. Intended for demo only.
 *
 * @author Mark Fisher
 * @author Jennifer Hickey
 * @author David Turanski
 */
public class RedisStreamServer extends StreamServer {

	public RedisStreamServer(StreamDeployer streamDeployer) {
		super(streamDeployer);
	}

	public static void main(String[] args) {
		try {
			bootstrap(args);
		}
		catch(RedisConnectionFailureException e) {
			final Log logger = LogFactory.getLog(RedisStreamServer.class);
			logger.fatal(e.getMessage());
			System.err.println("Redis does not seem to be running. Did you install and start Redis? " +
					"Please see the Getting Started section of the guide for instructions.");
			System.exit(1);
		}
	}

	private static void bootstrap(String[] args) {
		LettuceConnectionFactory connectionFactory = getConnectionFactory(args);
		connectionFactory.afterPropertiesSet();
		RedisStreamDeployer streamDeployer = new RedisStreamDeployer(connectionFactory);
		StreamServer server = new RedisStreamServer(streamDeployer);
		server.afterPropertiesSet();
		server.start();
	}

	private static LettuceConnectionFactory getConnectionFactory(String[] args) {
		if (args.length >= 2) {
			return new ExceptionWrappingLettuceConnectionFactory(args[0], Integer.parseInt(args[1]));
		}
		else {
			return new ExceptionWrappingLettuceConnectionFactory();
		}
	}

}
