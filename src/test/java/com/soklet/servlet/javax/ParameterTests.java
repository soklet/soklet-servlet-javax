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

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ParameterTests {
	@Test
	public void parameterValuesOnlyForRequestedName() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/p?one=a&one=b&two=c").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		String[] oneValues = httpServletRequest.getParameterValues("one");
		Assertions.assertArrayEquals(new String[]{"a", "b"}, oneValues);

		String[] twoValues = httpServletRequest.getParameterValues("two");
		Assertions.assertArrayEquals(new String[]{"c"}, twoValues);

		String[] missing = httpServletRequest.getParameterValues("none");
		Assertions.assertNull(missing);
	}

	@Test
	public void formParametersIgnoredAfterInputStream() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.getInputStream();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));
		Assertions.assertNull(httpServletRequest.getParameter("form"));
		Assertions.assertArrayEquals(new String[]{"1"}, httpServletRequest.getParameterValues("query"));
		Assertions.assertNull(httpServletRequest.getParameterValues("form"));
	}

	@Test
	public void formParametersIgnoredAfterReader() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.getReader();

		Assertions.assertNull(httpServletRequest.getParameter("form"));
		Assertions.assertNull(httpServletRequest.getParameterValues("form"));
	}

	@Test
	public void queryParametersDoNotConsumeBodyForInputStream() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("text/plain")))
				.body("body".getBytes(StandardCharsets.UTF_8))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));

		String body = new String(httpServletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		Assertions.assertEquals("body", body);
	}

	@Test
	public void queryParametersDoNotConsumeBodyForReader() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p?query=1")
				.headers(Map.of("Content-Type", Set.of("text/plain")))
				.body("body".getBytes(StandardCharsets.UTF_8))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("1", httpServletRequest.getParameter("query"));

		Assertions.assertEquals('b', httpServletRequest.getReader().read());
	}

	@Test
	public void inputStreamEmptyAfterParameterAccess() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("2", httpServletRequest.getParameter("form"));

		Assertions.assertEquals(-1, httpServletRequest.getInputStream().read());
	}

	@Test
	public void readerEmptyAfterParameterAccess() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.POST, "/p")
				.headers(Map.of("Content-Type", Set.of("application/x-www-form-urlencoded")))
				.body("form=2".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("2", httpServletRequest.getParameter("form"));

		Assertions.assertEquals(-1, httpServletRequest.getReader().read());
	}
}
