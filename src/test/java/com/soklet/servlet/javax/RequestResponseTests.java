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
import com.soklet.ResponseCookie;
import com.soklet.Utilities.EffectiveOriginResolver.TrustPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
		String contentType = format("text/plain; charset=%s; boundary=example", charset.name());

		Request request = Request.withRawUrl(HttpMethod.POST, "/testing?a=b&c=d")
				.headers(Map.of(
						"One", Set.of("Two, Three"),
						"Host", Set.of("www.soklet.com"),
						"X-Forwarded-Proto", Set.of("https"),
						"Content-Type", Set.of(contentType)
				))
				.body(bodyAsString.getBytes(charset))
				.build();

		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();

		Assertions.assertEquals("www.soklet.com", httpServletRequest.getServerName(), "Server name mismatch");
		Assertions.assertEquals(443, httpServletRequest.getServerPort(), "Server port mismatch");
		Assertions.assertEquals("/testing", httpServletRequest.getRequestURI(), "Request URI mismatch");
		Assertions.assertEquals(contentType, httpServletRequest.getContentType(), "Content-Type mismatch");
		Assertions.assertEquals(bodyAsString, httpServletRequest.getReader().lines().collect(Collectors.joining("")),
				"Body content mismatch");
	}

	@Test
	public void requestParameterReturnsFirstValue() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/testing?a=first&a=second").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("first", httpServletRequest.getParameter("a"));
	}

	@Test
	public void queryParametersUseServletDecoding() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/testing?name=red+blue&plus=a%2Bb").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		Assertions.assertEquals("red blue", httpServletRequest.getParameter("name"));
		Assertions.assertEquals("a+b", httpServletRequest.getParameter("plus"));
	}

	@Test
	public void requestAndResponse() throws IOException {
		Charset charset = StandardCharsets.UTF_16BE;
		String requestBodyAsString = "example body";

		Request request = Request.withRawUrl(HttpMethod.POST, "/testing?a=b&c=d")
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

		SokletHttpServletResponse httpServletResponse = SokletHttpServletResponse.fromRequest(request, SokletServletContext.fromDefaults());
		httpServletResponse.setStatus(201);
		httpServletResponse.addHeader("test", "one");
		httpServletResponse.addHeader("test", "two");
		httpServletResponse.addCookie(cookie);
		httpServletResponse.setCharacterEncoding(charset.name());
		httpServletResponse.getWriter().print(responseBodyAsString);

		MarshaledResponse marshaledResponse = httpServletResponse.toMarshaledResponse();

		String marshaledResponseBodyAsString = new String(marshaledResponse.getBody().get(), charset);
		ResponseCookie responseCookie = marshaledResponse.getCookies().stream().findFirst().orElse(null);

		Assertions.assertEquals(201, (int) marshaledResponse.getStatusCode(), "Status mismatch");
		Assertions.assertEquals(Set.of("one", "two"), marshaledResponse.getHeaders().get("test"), "Header mismatch");
		Assertions.assertEquals("cname", responseCookie.getName(), "Cookie name mismatch");
		Assertions.assertEquals("cvalue", responseCookie.getValue().get(), "Cookie value mismatch");
		Assertions.assertEquals("soklet.com", responseCookie.getDomain().get(), "Cookie domain mismatch");
		Assertions.assertEquals(Duration.ofSeconds(60L), responseCookie.getMaxAge().get(), "Cookie maxage mismatch");
		Assertions.assertEquals("/", responseCookie.getPath().get(), "Cookie path mismatch");
		Assertions.assertEquals(responseBodyAsString, marshaledResponseBodyAsString, "Body content mismatch");
	}

	@Test
	public void setCharacterEncodingAffectsFormParameters() throws IOException {
		String body = "name=caf%C3%A9";

		Request request = Request.withRawUrl(HttpMethod.POST, "/testing")
				.headers(Map.of(
						"Content-Type", Set.of("application/x-www-form-urlencoded")
				))
				.body(body.getBytes(StandardCharsets.US_ASCII))
				.build();

		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.setCharacterEncoding("UTF-8");
		Assertions.assertEquals("caf\u00E9", httpServletRequest.getParameter("name"));
	}

	@Test
	public void setCharacterEncodingAffectsQueryParameters() throws Exception {
		Request request = Request.withRawUrl(HttpMethod.GET, "/testing?name=caf%E9").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();
		httpServletRequest.setCharacterEncoding("ISO-8859-1");
		Assertions.assertEquals("caf\u00E9", httpServletRequest.getParameter("name"));
	}

	@Test
	public void session() {
		Request request = Request.withPath(HttpMethod.GET, "/testing").build();
		HttpServletRequest httpServletRequest = SokletHttpServletRequest.withRequest(request).build();

		HttpSession httpSession = httpServletRequest.getSession();
		httpSession.setAttribute("one", 1);

		Assertions.assertEquals(true, httpSession.isNew(), "Session is not new");
		Assertions.assertEquals(1, httpSession.getAttribute("one"), "Attribute has wrong value");

		httpSession.removeAttribute("one");

		Assertions.assertEquals(null, httpSession.getAttribute("one"), "Attribute has wrong value");
	}
}
