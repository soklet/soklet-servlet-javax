/*
 * Copyright 2024-2025 Revetware LLC.
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

import javax.annotation.concurrent.ThreadSafe;
import java.io.PrintWriter;

/*
 * Buffer size semantics: before vs after writing.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ResponseBufferSizeSemanticsTests {
	@Test
	public void setBufferSizeBeforeWritingIsAllowed() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setBufferSize(4096);
		Assertions.assertEquals(4096, resp.getBufferSize());
		// Write afterwards
		PrintWriter w = resp.getWriter();
		w.write("ok");
		w.flush();
	}

	@Test
	public void setBufferSizeAfterWritingShouldThrow() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		PrintWriter w = resp.getWriter();
		w.write("ok");
		Assertions.assertThrows(IllegalStateException.class, () -> resp.setBufferSize(8192));
	}

	@Test
	public void setBufferSizeZeroThrows() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		Assertions.assertThrows(IllegalArgumentException.class, () -> resp.setBufferSize(0));
	}

	@Test
	public void setBufferSizeNegativeThrows() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		Assertions.assertThrows(IllegalArgumentException.class, () -> resp.setBufferSize(-1));
	}

	@Test
	public void writeUnderBufferDoesNotCommitUntilFlush() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setBufferSize(8);
		PrintWriter w = resp.getWriter();
		w.write("ok");
		Assertions.assertFalse(resp.isCommitted());

		resp.flushBuffer();
		Assertions.assertTrue(resp.isCommitted());
	}

	@Test
	public void writeAtBufferSizeCommitsResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setBufferSize(4);
		resp.getOutputStream().write(new byte[]{1, 2, 3, 4});
		Assertions.assertTrue(resp.isCommitted());
	}
}
