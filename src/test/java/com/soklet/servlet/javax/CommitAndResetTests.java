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

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CommitAndResetTests {
	@Test
	public void writingCommitsResponseAndResetBufferAfterCommitThrows() throws Exception {
		SokletHttpServletResponse response = SokletHttpServletResponse.withRawPath("/p", SokletServletContext.withDefaults());
		PrintWriter pw = response.getWriter();
		pw.print("hello");
		pw.flush();

		Assertions.assertTrue(response.isCommitted(), "Response should be committed after writing");

		try {
			response.resetBuffer();
			Assertions.fail("resetBuffer should have thrown");
		} catch (IllegalStateException expected) {
			// ok
		}
	}

	@Test
	public void resetAllowsSwitchingWriters() throws Exception {
		SokletHttpServletResponse response = SokletHttpServletResponse.withRawPath("/p", SokletServletContext.withDefaults());
		response.getWriter(); // select writer
		response.reset();     // reset clears write method
		response.getOutputStream(); // now allowed
	}
}
