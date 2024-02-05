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
import com.soklet.core.MarshaledResponse;
import com.soklet.core.Request;
import com.soklet.core.ResponseCookie;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestResponseTests {
	@Test
	public void requestBasics() throws IOException {
		Charset charset = StandardCharsets.ISO_8859_1;
		String bodyAsString = "example body";

		Request request = new Request.Builder(HttpMethod.POST, "/testing?a=b&c=d")
				.headers(Map.of(
						"One", Set.of("Two, Three"),
						"Host", Set.of("www.soklet.com"),
						"X-Forwarded-Proto", Set.of("https"),
						"Content-Type", Set.of(format("text/plain; charset=%s", charset.name()))
				))
				.body(bodyAsString.getBytes(charset))
				.build();

		HttpServletRequest httpServletRequest = new SokletHttpServletRequest(request);

		Assert.assertEquals("Server name mismatch", "www.soklet.com", httpServletRequest.getServerName());
		Assert.assertEquals("Server port mismatch", 443, httpServletRequest.getServerPort());
		Assert.assertEquals("Request URI mismatch", "/testing", httpServletRequest.getRequestURI());
		Assert.assertEquals("Body content mismatch", bodyAsString,
				httpServletRequest.getReader().lines().collect(Collectors.joining("")));
	}

	@Test
	public void requestAndResponse() throws IOException {
		Charset charset = StandardCharsets.UTF_16BE;
		String requestBodyAsString = "example body";

		Request request = new Request.Builder(HttpMethod.POST, "/testing?a=b&c=d")
				.headers(Map.of(
						"Content-Type", Set.of(format("text/plain; charset=%s", charset.name()))
				))
				.body(requestBodyAsString.getBytes(charset))
				.build();

		Cookie cookie = new Cookie("cname", "cvalue");
		cookie.setDomain("soklet.com");
		cookie.setMaxAge(60);
		cookie.setPath("/");

		String responseBodyAsString = "response test";

		SokletHttpServletResponse httpServletResponse = new SokletHttpServletResponse(request);
		httpServletResponse.setStatus(201);
		httpServletResponse.addHeader("test", "one");
		httpServletResponse.addHeader("test", "two");
		httpServletResponse.addCookie(cookie);
		httpServletResponse.setCharacterEncoding(charset.name());
		httpServletResponse.getWriter().print(responseBodyAsString);

		MarshaledResponse marshaledResponse = httpServletResponse.toMarshaledResponse();

		String marshaledResponseBodyAsString = new String(marshaledResponse.getBody().get(), charset);
		ResponseCookie responseCookie = marshaledResponse.getCookies().stream().findFirst().orElse(null);

		Assert.assertEquals("Header mismatch", Set.of("one", "two"), marshaledResponse.getHeaders().get("test"));
		Assert.assertEquals("Cookie name mismatch", "cname", responseCookie.getName());
		Assert.assertEquals("Cookie value mismatch", "cvalue", responseCookie.getValue().get());
		Assert.assertEquals("Cookie domain mismatch", "soklet.com", responseCookie.getDomain().get());
		Assert.assertEquals("Cookie maxage mismatch", Duration.ofSeconds(60L), responseCookie.getMaxAge().get());
		Assert.assertEquals("Cookie path mismatch", "/", responseCookie.getPath().get());
		Assert.assertEquals("Body content mismatch", responseBodyAsString, marshaledResponseBodyAsString);
	}

	@Test
	public void session() {
		Request request = new Request.Builder(HttpMethod.GET, "/testing").build();
		HttpServletRequest httpServletRequest = new SokletHttpServletRequest(request);

		HttpSession httpSession = httpServletRequest.getSession();
		httpSession.setAttribute("one", 1);

		Assert.assertEquals("Session is not new", true, httpSession.isNew());
		Assert.assertEquals("Attribute has wrong value", 1, httpSession.getAttribute("one"));

		httpSession.removeAttribute("one");

		Assert.assertEquals("Attribute has wrong value", null, httpSession.getAttribute("one"));
	}
}
