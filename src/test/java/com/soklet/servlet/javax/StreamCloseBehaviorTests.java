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

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/*
 * Stream close semantics for servlet input/output streams.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StreamCloseBehaviorTests {
	@Test
	public void inputStreamReadAfterCloseThrows() throws Exception {
		ServletInputStream inputStream = SokletServletInputStream.fromInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
		inputStream.close();

		Assertions.assertThrows(IOException.class, inputStream::read);
	}

	@Test
	public void outputStreamWriteAfterCloseThrows() throws Exception {
		ServletOutputStream outputStream = SokletServletOutputStream.withOutputStream(new ByteArrayOutputStream()).build();
		outputStream.close();

		Assertions.assertThrows(IOException.class, () -> outputStream.write(1));
	}

	@Test
	public void outputStreamFlushAfterCloseThrows() throws Exception {
		ServletOutputStream outputStream = SokletServletOutputStream.withOutputStream(new ByteArrayOutputStream()).build();
		outputStream.close();

		Assertions.assertThrows(IOException.class, outputStream::flush);
	}
}
