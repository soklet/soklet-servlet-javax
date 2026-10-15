/*
 * Copyright 2024-2026 Revetware LLC.
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

package com.soklet.servlet.javax;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

public class ServletContextLogTests {
	@Test
	public void successiveMessagesAndExceptionsRemainSeparateLogEntries() {
		String separator = System.lineSeparator();
		StringWriter output = new StringWriter();
		var context = SokletServletContext.builder().logWriter(output).build();
		context.log("first");
		context.log("second" + separator);
		var failure = new IllegalStateException("boom");
		failure.setStackTrace(new StackTraceElement[0]);
		context.log("third", failure);
		context.log("fourth", null);
		context.log(null);
		context.log(null, (Throwable) null);
		Assertions.assertEquals(String.join(separator, "first", "second", "third",
				"java.lang.IllegalStateException: boom", "fourth", ""), output.toString());
	}
}
