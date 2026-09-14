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

import com.soklet.HttpServer;
import com.soklet.LifecyclePolicy;
import com.soklet.MarshaledResponse;
import com.soklet.ResourceMethodResolver;
import com.soklet.Soklet;
import com.soklet.SokletConfig;
import com.soklet.annotation.GET;
import javax.servlet.http.Cookie;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Verifies generated Servlet responses through the actual Soklet HTTP transport.
 */
@ThreadSafe
public class GeneratedResponseWireTests {
	private static final String ERROR_TEXT = "<script>alert('untrusted message')</script>";
	private static final int MAXIMUM_RESPONSE_BYTES = 16 * 1024;

	@Test
	@Timeout(30)
	public void generatedResponsesHaveSafeBodiesAndMatchingWireHeaders() throws Exception {
		int port;
		try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
			port = reservation.getLocalPort();
		}

		HttpServer server = HttpServer.withPort(port).host("127.0.0.1")
				.concurrency(1).requestHandlerConcurrency(1)
				.requestHeaderTimeout(Duration.ofSeconds(2))
				.requestHandlerTimeout(Duration.ofSeconds(3)).build();
		SokletConfig config = SokletConfig.withHttpServer(server)
				.resourceMethodResolver(ResourceMethodResolver.fromClasses(Set.of(Resources.class)))
				.lifecyclePolicy(LifecyclePolicy.builder()
						.startupTimeout(Duration.ofSeconds(5))
						.startupCancelationTimeout(Duration.ofSeconds(1))
						.gracefulShutdownTimeout(Duration.ofSeconds(2))
						.forcedShutdownTimeout(Duration.ofSeconds(1)).build()).build();

		try (Soklet owner = Soklet.fromConfig(config)) {
			owner.start();
			WireResponse html = readResponse(port, "/wire/error-html");
			Assertions.assertEquals("HTTP/1.1 400 Bad Request", html.status);
			Assertions.assertEquals("text/plain; charset=UTF-8", html.headers.get("Content-Type"));
			Assertions.assertArrayEquals(ERROR_TEXT.getBytes(StandardCharsets.UTF_8), html.body);
			assertMatchingLength(html);

			WireResponse error = readResponse(port, "/wire/error-headers");
			Assertions.assertEquals("HTTP/1.1 404 Not Found", error.status);
			Assertions.assertArrayEquals("Not Found".getBytes(StandardCharsets.US_ASCII), error.body);
			assertMatchingLength(error);
			assertOriginalRepresentationRemoved(error);
			Assertions.assertEquals("preserved", error.headers.get("X-Audit"));
			Assertions.assertEquals("default-src 'none'", error.headers.get("Content-Security-Policy"));
			Assertions.assertTrue(error.headers.get("Set-Cookie").startsWith("audit=preserved"));

			WireResponse redirect = readResponse(port, "/wire/redirect");
			Assertions.assertEquals("HTTP/1.1 302 Found", redirect.status);
			Assertions.assertEquals("http://localhost/a/b", redirect.headers.get("Location"));
			Assertions.assertEquals(0, redirect.body.length);
			assertMatchingLength(redirect);
			assertOriginalRepresentationRemoved(redirect);
		}
	}

	private static void assertMatchingLength(@NonNull WireResponse response) {
		Assertions.assertEquals(Integer.toString(response.body.length), response.headers.get("Content-Length"));
	}

	private static void assertOriginalRepresentationRemoved(@NonNull WireResponse response) {
		for (String name : List.of("Content-Encoding", "Content-Range", "ETag", "Content-MD5"))
			Assertions.assertFalse(response.headers.containsKey(name), name);
	}

	@NonNull
	private static WireResponse readResponse(int port, @NonNull String path) throws Exception {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
			socket.setSoTimeout(2_000);
			socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n"
					+ "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			socket.getOutputStream().flush();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			for (;;) {
				Assertions.assertTrue(System.nanoTime() < deadline, "Response deadline exceeded");
				int next = socket.getInputStream().read();
				if (next == -1)
					break;
				Assertions.assertTrue(output.size() < MAXIMUM_RESPONSE_BYTES, "Response byte limit exceeded");
				output.write(next);
			}
			byte[] bytes = output.toByteArray();
			String raw = new String(bytes, StandardCharsets.ISO_8859_1);
			int separator = raw.indexOf("\r\n\r\n");
			Assertions.assertTrue(separator >= 0, "Missing header terminator");
			String[] lines = raw.substring(0, separator).split("\r\n");
			Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (int index = 1; index < lines.length; index++) {
				int colon = lines[index].indexOf(':');
				Assertions.assertTrue(colon > 0, "Malformed response header");
				String name = lines[index].substring(0, colon);
				Assertions.assertNull(headers.put(name, lines[index].substring(colon + 1).trim()),
						"Unexpected duplicate header: " + name);
			}
			return new WireResponse(lines[0], headers, Arrays.copyOfRange(bytes, separator + 4, bytes.length));
		}
	}

	private record WireResponse(@NonNull String status, @NonNull Map<String, String> headers,
			byte @NonNull [] body) {}

	public static class Resources {
		@GET("/wire/error-html")
		@NonNull
		public MarshaledResponse errorHtml() throws IOException {
			SokletHttpServletResponse response = newResponse();
			response.setContentType("text/html; charset=UTF-8");
			response.sendError(400, ERROR_TEXT);
			return response.toMarshaledResponse();
		}

		@GET("/wire/error-headers")
		@NonNull
		public MarshaledResponse errorHeaders() throws IOException {
			SokletHttpServletResponse response = newResponse();
			setOriginalRepresentation(response);
			response.setHeader("X-Audit", "preserved");
			response.setHeader("Content-Security-Policy", "default-src 'none'");
			response.addCookie(new Cookie("audit", "preserved"));
			response.sendError(404);
			return response.toMarshaledResponse();
		}

		@GET("/wire/redirect")
		@NonNull
		public MarshaledResponse redirect() throws IOException {
			SokletHttpServletResponse response = newResponse();
			setOriginalRepresentation(response);
			response.getWriter().write("old");
			response.sendRedirect("/a//../b");
			return response.toMarshaledResponse();
		}

		@NonNull
		private static SokletHttpServletResponse newResponse() {
			return SokletHttpServletResponse.fromRawPath("/wire", SokletServletContext.fromDefaults());
		}

		private static void setOriginalRepresentation(@NonNull SokletHttpServletResponse response) {
			response.setContentLength(100);
			response.setHeader("Content-Encoding", "gzip");
			response.setHeader("Content-Range", "bytes 0-99/100");
			response.setHeader("ETag", "\"old\"");
			response.setHeader("Content-MD5", "old");
		}
	}
}
