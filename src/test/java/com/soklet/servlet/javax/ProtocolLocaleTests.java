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
import com.soklet.EffectiveOriginResolver.TrustPolicy;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Tests actual request and redirect conversion in isolated format-locale JVMs. */
public class ProtocolLocaleTests {
	@Test
	@Timeout(90)
	public void protocolAuthoritiesUseAsciiDigitsRegardlessOfJvmLocale() {
		Assertions.assertAll("Child format-locale probes",
				List.of("ar", "fa", "en").stream()
						.map(language -> (Executable) () -> runProbe(language)));
	}

	private static void runProbe(@NonNull String language) throws Exception {
		String country = language.equals("ar") ? "EG" : language.equals("fa") ? "IR" : "US";
		String classpath = System.getProperty("surefire.test.class.path",
				System.getProperty("java.class.path"));
		Path output = Files.createTempFile("soklet-servlet-locale-", ".log");
		Process process = null;
		try {
			process = new ProcessBuilder(
					Path.of(System.getProperty("java.home"), "bin", "java").toString(),
					"-Duser.language=" + language, "-Duser.country=" + country,
					"-Duser.language.format=" + language, "-Duser.country.format=" + country,
					"-cp", classpath, ProtocolLocaleTests.class.getName(), language)
					.redirectErrorStream(true).redirectOutput(output.toFile()).start();
			Assertions.assertTrue(process.waitFor(20, TimeUnit.SECONDS),
					"Locale probe timed out: " + language);
			Assertions.assertTrue(Files.size(output) <= 128 * 1024, "Unexpectedly large locale-probe output");
			String log = Files.readString(output, StandardCharsets.UTF_8);
			Assertions.assertEquals(0, process.exitValue(), language + ": " + log);
			Assertions.assertTrue(log.contains("PASS protocol authorities " + language), log);
		} finally {
			try {
				if (process != null && process.isAlive()) {
					process.destroyForcibly();
					Assertions.assertTrue(process.waitFor(2, TimeUnit.SECONDS), "Locale probe did not terminate");
				}
			} finally {
				Files.deleteIfExists(output);
			}
		}
	}

	public static void main(@NonNull String[] args) throws Exception {
		Assertions.assertEquals(args[0], Locale.getDefault(Locale.Category.FORMAT).getLanguage());
		if (!args[0].equals("en"))
			Assertions.assertNotEquals('0', DecimalFormatSymbols.getInstance().getZeroDigit(),
					"Probe must run with a non-Latin default numbering system");
		Assertions.assertAll(
				() -> assertScenario("example.com", 8080, false, false, "http://example.com:8080", 8080),
				() -> assertScenario("example.com", 8443, true, false, "http://example.com:8443", 8443),
				() -> assertScenario("2001:db8::1", 8080, false, false, "http://[2001:db8::1]:8080", 8080),
				() -> assertScenario("example.com", 80, false, false, "http://example.com", -1),
				() -> assertScenario("example.com", 8443, true, true, "https://example.com:8443", 8443),
				() -> assertScenario("example.com", 443, true, true, "https://example.com", 443));
		System.out.println("PASS protocol authorities " + args[0]);
	}

	private static void assertScenario(@NonNull String host, int port, boolean hostHeader,
			boolean forwardedHttps, @NonNull String origin, int expectedUriPort) throws Exception {
		Request.RawBuilder source = Request.withRawUrl(HttpMethod.GET, "/root/path?source=1");
		if (hostHeader) {
			source.headers(forwardedHttps
					? Map.of("Host", Set.of(host + ":" + port), "X-Forwarded-Proto", Set.of("https"))
					: Map.of("Host", Set.of(host + ":" + port)));
		}
		SokletHttpServletRequest request = SokletHttpServletRequest.withRequest(source.build())
				.host(host).port(port).forwardedHeaderTrustPolicy(forwardedHttps
						? TrustPolicy.TRUST_ALL : TrustPolicy.TRUST_NONE).build();
		Assertions.assertAll(origin,
				() -> {
					String url = request.getRequestURL().toString();
					// Trusted forwarding preserves an explicitly supplied default port in the
					// request URL; redirect-base construction continues to omit that port.
					String requestOrigin = forwardedHttps && port == 443 ? origin + ":443" : origin;
					Assertions.assertEquals(requestOrigin + "/root/path", url);
					Assertions.assertTrue(url.chars().allMatch(character -> character <= 0x7f), url);
					URI uri = URI.create(url);
					Assertions.assertNotNull(uri.getHost());
					Assertions.assertEquals(expectedUriPort, uri.getPort());
				},
				() -> assertRedirect(request, "next?x=1#fragment", origin + "/root/next?x=1#fragment"),
				() -> assertRedirect(request, "/next", origin + "/next"));
	}

	private static void assertRedirect(@NonNull SokletHttpServletRequest request,
			@NonNull String target, @NonNull String expected) throws Exception {
		SokletHttpServletResponse response = SokletHttpServletResponse.fromHttpServletRequest(request);
		response.sendRedirect(target);
		MarshaledResponse marshaled = response.toMarshaledResponse();
		Assertions.assertEquals(302, marshaled.getStatusCode());
		Assertions.assertEquals(Set.of(expected), marshaled.getHeaders().get("Location"));
		Assertions.assertTrue(expected.chars().allMatch(character -> character <= 0x7f));
	}
}
