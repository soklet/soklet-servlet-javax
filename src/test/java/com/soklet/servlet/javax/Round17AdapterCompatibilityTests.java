package com.soklet.servlet.javax;

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Round17AdapterCompatibilityTests {
	@TempDir Path directory;

	@Test void classpathFileStreamsEscapeFilenameCharactersWithoutDecodingExistingEscapesTwice() throws Exception {
		for (String name : List.of("square[brackets]{braces}|caret^`quote\"<>\\.txt", "a+b café.txt", "percent%oops.txt")) {
			Path file = Files.writeString(directory.resolve(name), "content:" + name);
			for (URL resource : List.of(new URL("file:" + file), new URL("file://localhost" + file), file.toUri().toURL()))
				assertResourceContent(resource, "content:" + name);
		}
		Path literalEscape = Files.writeString(directory.resolve("literal%20-plus+.txt"), "literal percent escape");
		assertResourceContent(literalEscape.toUri().toURL(), "literal percent escape");
		assertResourceContent(new URL("file://localhost" + literalEscape.toUri().getRawPath()), "literal percent escape");
	}

	@Test void dateSettersRejectUnrepresentableYearsBeforeChangingHeaders() {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.setDateHeader("X-Date", 0);
		response.addDateHeader("X-Date", 1_000);
		var original = List.copyOf(response.getHeaders("X-Date"));
		for (long value : new long[]{Long.MIN_VALUE, Long.MAX_VALUE,
				Instant.parse("0000-12-31T23:59:59Z").toEpochMilli(), Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli()}) {
			assertThrows(IllegalArgumentException.class, () -> response.setDateHeader("X-Date", value));
			assertEquals(original, List.copyOf(response.getHeaders("X-Date")));
			assertThrows(IllegalArgumentException.class, () -> response.addDateHeader("X-Date", value));
			assertEquals(original, List.copyOf(response.getHeaders("X-Date")));
		}
		response.setDateHeader("X-Minimum", Instant.parse("0001-01-01T00:00:00Z").toEpochMilli());
		response.setDateHeader("X-Maximum", Instant.parse("9999-12-31T23:59:59Z").toEpochMilli());
		assertEquals("Mon, 01 Jan 0001 00:00:00 GMT", response.getHeader("X-Minimum"));
		assertEquals("Fri, 31 Dec 9999 23:59:59 GMT", response.getHeader("X-Maximum"));
	}

	@Test void committedDateSettersRemainNoOpsEvenForUnrepresentableDates() throws Exception {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.setHeader("X-Date", "preserved");
		response.flushBuffer();
		assertDoesNotThrow(() -> response.setDateHeader("X-Date", Long.MAX_VALUE));
		assertDoesNotThrow(() -> response.addDateHeader("X-Date", Long.MIN_VALUE));
		assertEquals(List.of("preserved"), List.copyOf(response.getHeaders("X-Date")));
	}

	@Test void boundHeadPreservesExplicitSelectedRepresentationMetadataInBothConversions() {
		var request = Request.fromPath(HttpMethod.HEAD, "/download");
		var response = SokletHttpServletResponse.fromRequest(request, SokletServletContext.fromDefaults());
		response.setContentType("text/plain");
		response.setContentLengthLong(123);
		response.setHeader("Content-Encoding", "gzip");
		response.setHeader("Vary", "Accept-Encoding");
		var marshaled = response.toMarshaledResponse();
		var application = response.toResponse();
		assertTrue(marshaled.getBody().isEmpty());
		assertTrue(application.getBody().isEmpty());
		assertEquals(marshaled.getHeaders(), application.getHeaders());
		assertEquals(List.of("123"), marshaled.getHeaders().get("Content-Length"));
		assertEquals(List.of("gzip"), marshaled.getHeaders().get("Content-Encoding"));
		assertEquals(List.of("Accept-Encoding"), marshaled.getHeaders().get("Vary"));
	}

	private void assertResourceContent(URL resource, String expected) throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
				@Override public URL getResource(String name) { return resource; }
			});
			var context = SokletServletContext.builder().classpathResourceRoot("web").build();
			try (var stream = context.getResourceAsStream("/resource")) {
				assertNotNull(stream, resource.toString());
				assertEquals(expected, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			}
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}
}
