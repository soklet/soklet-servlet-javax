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
import com.soklet.InstanceProvider;
import com.soklet.LifecycleObserver;
import com.soklet.LogEvent;
import com.soklet.MarshaledResponse;
import com.soklet.MarshaledResponseBody;
import com.soklet.Request;
import com.soklet.ResourceMethodResolver;
import com.soklet.SimulatorConfig;
import com.soklet.SokletSimulator;
import com.soklet.annotation.GET;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exercises both adapter conversions through Soklet's normal response handling.
 */
public class BodylessResponseTests {
	@Test
	public void emptyBodylessBuffersHaveNoBody() {
		Assertions.assertAll(
				() -> Assertions.assertTrue(servletResponse(204, new byte[0]).toResponse().getBody().isEmpty(), "Response status 204"),
				() -> Assertions.assertTrue(servletResponse(304, new byte[0]).toResponse().getBody().isEmpty(), "Response status 304"),
				() -> Assertions.assertTrue(servletResponse(204, new byte[0]).toMarshaledResponse().getBody().isEmpty(), "MarshaledResponse status 204"),
				() -> Assertions.assertTrue(servletResponse(304, new byte[0]).toMarshaledResponse().getBody().isEmpty(), "MarshaledResponse status 304"));
	}

	@Test
	public void emptyBodylessResponsesRemainBodylessThroughSoklet() {
		Assertions.assertAll(
				() -> assertBodylessResult(204, false),
				() -> assertBodylessResult(304, false),
				() -> assertBodylessResult(204, true),
				() -> assertBodylessResult(304, true));
	}

	@Test
	public void nonemptyBodylessBuffersRemainAvailableForCoreRejection() throws IOException {
		byte[] body = new byte[]{1};
		for (int status : new int[]{204, 304}) {
			SokletHttpServletResponse servlet = servletResponse(status, body);
			Assertions.assertArrayEquals(body, (byte[]) servlet.toResponse().getBody().orElseThrow());
			Assertions.assertArrayEquals(body, bytes(servlet.toMarshaledResponse()));
		}
	}

	@Test
	public void nonemptyBodylessResponsesAreRejectedThroughSoklet() {
		for (int status : new int[]{204, 304}) {
			for (boolean marshaled : new boolean[]{false, true})
				Assertions.assertEquals(500, execute(status, new byte[]{1}, marshaled).getStatusCode());
		}
	}

	@Test
	public void emptyOkConversionPreservesBodyPresenceAndType() throws IOException {
		SokletHttpServletResponse servlet = servletResponse(200, new byte[0]);
		Assertions.assertArrayEquals(new byte[0], (byte[]) servlet.toResponse().getBody().orElseThrow());
		Assertions.assertInstanceOf(MarshaledResponseBody.Bytes.class, servlet.toMarshaledResponse().getBody().orElseThrow());
	}

	@Test
	public void emptyOkResponsePreservesItsMarshalingRepresentation() {
		for (boolean marshaled : new boolean[]{false, true}) {
			MarshaledResponse response = execute(200, new byte[0], marshaled);
			Assertions.assertEquals(200, response.getStatusCode());
			Assertions.assertArrayEquals(new byte[0], bytes(response));
			if (marshaled)
				Assertions.assertFalse(response.getHeaders().containsKey("Content-Type"));
			else
				Assertions.assertEquals(Set.of("application/octet-stream"), response.getHeaders().get("Content-Type"));
			assertHeaderAndCookie(response);
		}
	}

	@Test
	public void ordinaryContentHeadersAndCookiesSurviveBothPaths() {
		byte[] body = "servlet response".getBytes(StandardCharsets.UTF_8);
		for (boolean marshaled : new boolean[]{false, true}) {
			MarshaledResponse response = execute(201, body, marshaled);
			Assertions.assertEquals(201, response.getStatusCode());
			Assertions.assertArrayEquals(body, bytes(response));
			assertHeaderAndCookie(response);
		}
	}

	@Test
	public void emptyInformationalResponsesAreNotPromotedToSupportedFinalResponses() throws IOException {
		SokletHttpServletResponse servlet = servletResponse(103, new byte[0]);
		Assertions.assertTrue(servlet.toResponse().getBody().isPresent());
		Assertions.assertTrue(servlet.toMarshaledResponse().getBody().isPresent());
		for (boolean marshaled : new boolean[]{false, true})
			Assertions.assertEquals(500, execute(103, new byte[0], marshaled).getStatusCode());
	}

	private static void assertBodylessResult(int status, boolean marshaled) {
		MarshaledResponse response = execute(status, new byte[0], marshaled);
		Assertions.assertEquals(status, response.getStatusCode(), "Marshaled conversion: " + marshaled);
		Assertions.assertTrue(response.getBody().isEmpty());
		assertHeaderAndCookie(response);
	}

	private static void assertHeaderAndCookie(MarshaledResponse response) {
		Assertions.assertEquals(Set.of("preserved"), response.getHeaders().get("X-Servlet-Test"));
		Assertions.assertTrue(response.getCookies().stream().anyMatch(cookie ->
				cookie.getName().equals("session") && cookie.getValue().orElseThrow().equals("preserved")));
	}

	private static byte[] bytes(MarshaledResponse response) {
		return Assertions.assertInstanceOf(MarshaledResponseBody.Bytes.class,
				response.getBody().orElseThrow()).getBytes();
	}

	private static SokletHttpServletResponse servletResponse(int status, byte[] body) throws IOException {
		SokletHttpServletResponse response = SokletHttpServletResponse.fromRawPath("/servlet", SokletServletContext.fromDefaults());
		response.setStatus(status);
		response.setHeader("X-Servlet-Test", "preserved");
		response.addCookie(new Cookie("session", "preserved"));
		response.getOutputStream().write(body);
		return response;
	}

	private static MarshaledResponse execute(int status, byte[] body, boolean marshaled) {
		ServletResource resource = new ServletResource(status, body, marshaled);
		InstanceProvider defaults = InstanceProvider.defaultInstance();
		SimulatorConfig config = SimulatorConfig.builder()
				.httpServer()
				.resourceMethodResolver(ResourceMethodResolver.fromClasses(Set.of(ServletResource.class)))
				.instanceProvider(new InstanceProvider() {
					@Override
					@NonNull
					public <T> T provide(@NonNull Class<T> instanceClass) {
						return instanceClass == ServletResource.class ? instanceClass.cast(resource) : defaults.provide(instanceClass);
					}
				})
				.lifecycleObserver(new LifecycleObserver() {
					@Override
					public void didReceiveLogEvent(@NonNull LogEvent logEvent) {
						// Expected validation failures are asserted through their normal HTTP results.
					}
				})
				.build();
		AtomicReference<MarshaledResponse> result = new AtomicReference<>();
		SokletSimulator.run(config, simulator -> result.set(simulator
				.performHttpRequest(Request.fromPath(HttpMethod.GET, "/servlet")).getMarshaledResponse()));
		return result.get();
	}

	public static class ServletResource {
		private final int status;
		private final byte[] body;
		private final boolean marshaled;

		ServletResource(int status, byte[] body, boolean marshaled) {
			this.status = status;
			this.body = body;
			this.marshaled = marshaled;
		}

		@GET("/servlet")
		public Object response() throws IOException {
			SokletHttpServletResponse response = servletResponse(this.status, this.body);
			return this.marshaled ? response.toMarshaledResponse() : response.toResponse();
		}
	}
}
