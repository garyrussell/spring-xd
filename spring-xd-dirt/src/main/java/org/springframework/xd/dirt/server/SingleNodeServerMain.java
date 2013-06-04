/*
 * Copyright 2002-2013 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.xd.dirt.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.springframework.xd.dirt.stream.RedisStreamServer;

/**
 * Launches a single node XD server with an embedded Admin service and Local Channel Registry, suitable for testing and 
 * demonstration purposes.
 * 
 * @author David Turanski
 *
 */
public class SingleNodeServerMain {
	private static final Log logger = LogFactory.getLog(SingleNodeServerMain.class);
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SingleNodeServerOptions options = new  SingleNodeServerOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			logger.error(e.getMessage());
			parser.printUsage(System.err);
			System.exit(1);
		}
		
		if (options.isShowHelp()) {
			parser.printUsage(System.err);
			System.exit(0);
		}

		RedisStreamServer.main(new String[] {options.getRedisHost(), Integer.toString(options.getRedisPort())});

	}

}
