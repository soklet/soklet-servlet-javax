/*
 * Copyright 2024 Revetware LLC.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
class SokletServletPrintWriter extends PrintWriter {
	@Nonnull
	private final Consumer<SokletServletPrintWriter> writeOccurredCallback;
	@Nonnull
	private final Consumer<SokletServletPrintWriter> committedCallback;
	@Nonnull
	private Boolean committed = false;

	public SokletServletPrintWriter(@Nonnull Writer writer,
																	@Nullable Consumer<SokletServletPrintWriter> writeOccurredCallback,
																	@Nullable Consumer<SokletServletPrintWriter> committedCallback) {
		super(requireNonNull(writer), true);

		if (writeOccurredCallback == null)
			writeOccurredCallback = (ignored) -> {};

		if (committedCallback == null)
			committedCallback = (ignored) -> {};

		this.writeOccurredCallback = writeOccurredCallback;
		this.committedCallback = committedCallback;
	}

	@Nonnull
	public Boolean getCommitted() {
		return this.committed;
	}

	protected void setCommitted(@Nonnull Boolean committed) {
		this.committed = committed;
	}

	@Nonnull
	protected Consumer<SokletServletPrintWriter> getWriteOccurredCallback() {
		return this.writeOccurredCallback;
	}

	@Nonnull
	protected Consumer<SokletServletPrintWriter> getCommittedCallback() {
		return this.committedCallback;
	}

// Implementation of PrintWriter methods below:

	@Override
	public void write(@Nonnull char[] buf,
										int off,
										int len) {
		requireNonNull(buf);

		super.write(buf, off, len);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void write(@Nonnull String s,
										int off,
										int len) {
		requireNonNull(s);

		super.write(s, off, len);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void write(int c) {
		super.write(c);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void write(@Nonnull char[] buf) {
		requireNonNull(buf);

		super.write(buf);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void write(@Nonnull String s) {
		requireNonNull(s);

		super.write(s);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(boolean b) {
		super.print(b);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(char c) {
		super.print(c);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(int i) {
		super.print(i);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(long l) {
		super.print(l);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(float f) {
		super.print(f);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(double d) {
		super.print(d);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(@Nonnull char[] s) {
		requireNonNull(s);

		super.print(s);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(@Nullable String s) {
		super.print(s);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void print(@Nullable Object obj) {
		super.print(obj);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println() {
		super.println();
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(boolean x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(char x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(int x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(long x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(float x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(double x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(char[] x) {
		requireNonNull(x);

		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(@Nullable String x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	public void println(@Nullable Object x) {
		super.println(x);
		super.flush();
		getWriteOccurredCallback().accept(this);
	}

	@Override
	@Nonnull
	public PrintWriter printf(@Nonnull String format,
														@Nullable Object... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.printf(format, args);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter printf(@Nullable Locale l,
														@Nonnull String format,
														@Nullable Object... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.printf(l, format, args);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter format(@Nonnull String format,
														@Nullable Object... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.format(format, args);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter format(@Nullable Locale l,
														@Nonnull String format,
														@Nullable Object... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.format(l, format, args);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter append(@Nullable CharSequence csq) {
		PrintWriter printWriter = super.append(csq);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter append(@Nullable CharSequence csq,
														int start,
														int end) {
		PrintWriter printWriter = super.append(csq, start, end);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	@Nonnull
	public PrintWriter append(char c) {
		PrintWriter printWriter = super.append(c);
		super.flush();
		getWriteOccurredCallback().accept(this);
		return printWriter;
	}

	@Override
	public void flush() {
		super.flush();

		if (!getCommitted()) {
			setCommitted(true);
			getCommittedCallback().accept(this);
		}
	}

	@Override
	public void close() {
		super.flush();
		super.close();

		if (!getCommitted()) {
			setCommitted(true);
			getCommittedCallback().accept(this);
		}
	}
}
