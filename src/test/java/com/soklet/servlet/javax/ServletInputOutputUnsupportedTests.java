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
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/*
 * Tests for unsupported async IO features on servlet streams.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ServletInputOutputUnsupportedTests {
	@Test
	public void setReadListenerThrows() {
		ServletInputStream in = SokletServletInputStream.withInputStream(new ByteArrayInputStream(new byte[]{}));
		Assertions.assertThrows(IllegalStateException.class, () -> in.setReadListener(new ReadListener() {
			@Override
			public void onDataAvailable() {}

			@Override
			public void onAllDataRead() {}

			@Override
			public void onError(Throwable t) {}
		}));
	}

	@Test
	public void setWriteListenerThrows() {
		ServletOutputStream out = SokletServletOutputStream.withOutputStream(new ByteArrayOutputStream()).build();
		Assertions.assertThrows(IllegalStateException.class, () -> out.setWriteListener(new WriteListener() {
			@Override
			public void onWritePossible() {}

			@Override
			public void onError(Throwable t) {}
		}));
	}
}
