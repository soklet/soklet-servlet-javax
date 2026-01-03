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
import com.soklet.MarshaledResponse;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

/*
 * Verify that ServletContext default encodings are honored.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ContextDefaultEncodingTests {
	@Test
	public void requestUsesContextDefaultEncoding() throws Exception {
		SokletServletContext context = SokletServletContext.withDefaults();
		context.setRequestCharacterEncoding("UTF-16BE");

		String text = "café";
		Charset charset = Charset.forName("UTF-16BE");

		Request request = Request.withPath(HttpMethod.POST, "/echo")
				.headers(Map.of("Content-Type", Set.of("text/plain")))
				.body(text.getBytes(charset))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request)
				.servletContext(context)
				.build();

		Assertions.assertEquals(text, http.getReader().readLine());
	}

	@Test
	public void responseUsesContextDefaultEncoding() throws Exception {
		SokletServletContext context = SokletServletContext.withDefaults();
		context.setResponseCharacterEncoding("UTF-16LE");

		String text = "café";
		Charset charset = Charset.forName("UTF-16LE");

		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", context);
		resp.getWriter().write(text);

		MarshaledResponse mr = resp.toMarshaledResponse();
		Assertions.assertArrayEquals(text.getBytes(charset), mr.getBody().orElse(new byte[]{}));
	}

	@Test
	public void invalidRequestCharacterEncodingIsIgnored() {
		SokletServletContext context = SokletServletContext.withDefaults();
		context.setRequestCharacterEncoding("no-such-charset");
		Assertions.assertEquals("UTF-8", context.getRequestCharacterEncoding());
	}

	@Test
	public void invalidResponseCharacterEncodingIsIgnored() {
		SokletServletContext context = SokletServletContext.withDefaults();
		context.setResponseCharacterEncoding("no-such-charset");
		Assertions.assertEquals("UTF-8", context.getResponseCharacterEncoding());
	}
}
