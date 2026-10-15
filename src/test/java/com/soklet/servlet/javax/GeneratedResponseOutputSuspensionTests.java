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

import javax.servlet.ServletOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class GeneratedResponseOutputSuspensionTests {
	@Test
	public void errorsAndRedirectsDiscardLaterWriterOutput() throws Exception {
		for (boolean acquireBefore : new boolean[]{false, true}) {
			for (int action = 0; action < 3; action++) {
				SokletHttpServletResponse response = newResponse();
				PrintWriter writer = acquireBefore ? response.getWriter() : null;
				if (writer != null)
					writer.write("discarded original body");
				finishResponse(response, action);
				if (writer == null)
					writer = response.getWriter();
				writer.write("SECRET");
				writer.print('!');
				writer.flush();
				writer.close();
				assertGeneratedBody(response, action);
			}
		}
	}

	@Test
	public void errorsAndRedirectsDiscardLaterStreamOutput() throws Exception {
		for (boolean acquireBefore : new boolean[]{false, true}) {
			for (int action = 0; action < 3; action++) {
				SokletHttpServletResponse response = newResponse();
				ServletOutputStream output = acquireBefore ? response.getOutputStream() : null;
				if (output != null)
					output.write("discarded original body".getBytes(StandardCharsets.US_ASCII));
				finishResponse(response, action);
				if (output == null)
					output = response.getOutputStream();
				output.write("SECRET".getBytes(StandardCharsets.US_ASCII));
				output.write('!');
				output.flush();
				output.close();
				assertGeneratedBody(response, action);
			}
		}
	}

	@Test
	public void ordinaryCommitStillAllowsSubsequentBodyWrites() throws Exception {
		SokletHttpServletResponse response = newResponse();
		PrintWriter writer = response.getWriter();
		writer.write("before");
		writer.flush();
		Assertions.assertTrue(response.isCommitted());
		writer.write("after");
		writer.flush();
		Assertions.assertArrayEquals("beforeafter".getBytes(StandardCharsets.US_ASCII),
				MarshaledResponseTestSupport.bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	private static void finishResponse(SokletHttpServletResponse response, int action) throws Exception {
		if (action == 0)
			response.sendError(404);
		else if (action == 1)
			response.sendError(403, "denied");
		else
			response.sendRedirect("/login");
	}

	private static void assertGeneratedBody(SokletHttpServletResponse response, int action) {
		String expectedBody = action == 0 ? "Not Found" : action == 1 ? "denied" : "";
		Assertions.assertTrue(response.isCommitted());
		Assertions.assertEquals(action == 0 ? 404 : action == 1 ? 403 : 302, response.getStatus());
		Assertions.assertArrayEquals(expectedBody.getBytes(StandardCharsets.US_ASCII),
				MarshaledResponseTestSupport.bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	private static SokletHttpServletResponse newResponse() {
		return SokletHttpServletResponse.fromRawPath("/private", SokletServletContext.fromDefaults());
	}
}
