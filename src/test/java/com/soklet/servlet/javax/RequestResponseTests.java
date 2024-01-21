/*
 * Copyright 2024 Revetware LLC.
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

import com.soklet.core.HttpMethod;
import com.soklet.core.Request;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestResponseTests {
	@Test
	public void testRequestBasics() throws IOException {
		String bodyAsString = "example body";

		Request request = new Request.Builder(HttpMethod.GET, "/testing?a=b&c=d")
				.headers(Map.of("One", Set.of("Two, Three")))
				.body(bodyAsString.getBytes(StandardCharsets.UTF_8))
				.build();

		HttpServletRequest httpServletRequest = new SokletHttpServletRequest(request);

		Assert.assertEquals("Request URI mismatch", "/testing", httpServletRequest.getRequestURI());
		Assert.assertEquals("Body content mismatch", bodyAsString,
				httpServletRequest.getReader().lines().collect(Collectors.joining("")));
	}
}
