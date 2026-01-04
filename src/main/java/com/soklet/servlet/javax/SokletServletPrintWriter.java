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

import com.soklet.servlet.javax.SokletServletPrintWriterEvent.CharAppended;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.CharSequenceAppended;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.CharWritten;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.CharsWritten;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.FormatPerformed;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.NewlinePrinted;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.PrintfPerformed;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.StringWritten;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.ValuePrinted;
import com.soklet.servlet.javax.SokletServletPrintWriterEvent.ValueWithNewlinePrinted;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Special implementation of {@link PrintWriter}, designed for use with {@link SokletHttpServletResponse}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletServletPrintWriter extends PrintWriter {
	@NonNull
	private final BiConsumer<@NonNull SokletServletPrintWriter, @NonNull SokletServletPrintWriterEvent> onWriteOccurred;
	@NonNull
	private final Consumer<@NonNull SokletServletPrintWriter> onWriteFinalized;
	@NonNull
	private Boolean writeFinalized = false;

	@NonNull
	public static Builder withWriter(@NonNull Writer writer) {
		return new Builder(writer);
	}

	private SokletServletPrintWriter(@NonNull Builder builder) {
		super(requireNonNull(builder.writer), true);
		this.onWriteOccurred = builder.onWriteOccurred != null ? builder.onWriteOccurred : (ignored1, ignored2) -> {};
		this.onWriteFinalized = builder.onWriteFinalized != null ? builder.onWriteFinalized : (ignored) -> {};
	}

	/**
	 * Builder used to construct instances of {@link SokletServletPrintWriter}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://www.revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@NonNull
		private Writer writer;
		@Nullable
		private BiConsumer<@NonNull SokletServletPrintWriter, @NonNull SokletServletPrintWriterEvent> onWriteOccurred;
		@Nullable
		private Consumer<@NonNull SokletServletPrintWriter> onWriteFinalized;

		@NonNull
		private Builder(@NonNull Writer writer) {
			requireNonNull(writer);
			this.writer = writer;
		}

		@NonNull
		public Builder writer(@NonNull Writer writer) {
			requireNonNull(writer);
			this.writer = writer;
			return this;
		}

		@NonNull
		public Builder onWriteOccurred(
				@Nullable BiConsumer<@NonNull SokletServletPrintWriter, @NonNull SokletServletPrintWriterEvent> onWriteOccurred) {
			this.onWriteOccurred = onWriteOccurred;
			return this;
		}

		@NonNull
		public Builder onWriteFinalized(@Nullable Consumer<@NonNull SokletServletPrintWriter> onWriteFinalized) {
			this.onWriteFinalized = onWriteFinalized;
			return this;
		}

		@NonNull
		public SokletServletPrintWriter build() {
			return new SokletServletPrintWriter(this);
		}
	}

	@NonNull
	private Boolean getWriteFinalized() {
		return this.writeFinalized;
	}

	private void setWriteFinalized(@NonNull Boolean writeFinalized) {
		requireNonNull(writeFinalized);
		this.writeFinalized = writeFinalized;
	}

	@NonNull
	private BiConsumer<@NonNull SokletServletPrintWriter, @NonNull SokletServletPrintWriterEvent> getOnWriteOccurred() {
		return this.onWriteOccurred;
	}

	@NonNull
	private Consumer<@NonNull SokletServletPrintWriter> getOnWriteFinalized() {
		return this.onWriteFinalized;
	}

// Implementation of PrintWriter methods below:

	@Override
	public void write(@NonNull char[] buf,
										int off,
										int len) {
		requireNonNull(buf);

		super.write(buf, off, len);
		super.flush();
		getOnWriteOccurred().accept(this, new CharsWritten(buf, off, len));
	}

	@Override
	public void write(@NonNull String s,
										int off,
										int len) {
		requireNonNull(s);

		super.write(s, off, len);
		super.flush();
		getOnWriteOccurred().accept(this, new StringWritten(s, off, len));
	}

	@Override
	public void write(int c) {
		super.write(c);
		super.flush();
		getOnWriteOccurred().accept(this, new CharWritten(c));
	}

	@Override
	public void write(@NonNull char[] buf) {
		requireNonNull(buf);

		super.write(buf);
		super.flush();
		getOnWriteOccurred().accept(this, new CharsWritten(buf, 0, buf.length));
	}

	@Override
	public void write(@NonNull String s) {
		requireNonNull(s);

		super.write(s);
		super.flush();
		getOnWriteOccurred().accept(this, new StringWritten(s, 0, s.length()));
	}

	@Override
	public void print(boolean b) {
		super.print(b);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(b));
	}

	@Override
	public void print(char c) {
		super.print(c);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(c));
	}

	@Override
	public void print(int i) {
		super.print(i);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(i));
	}

	@Override
	public void print(long l) {
		super.print(l);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(l));
	}

	@Override
	public void print(float f) {
		super.print(f);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(f));
	}

	@Override
	public void print(double d) {
		super.print(d);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(d));
	}

	@Override
	public void print(@NonNull char[] s) {
		requireNonNull(s);

		super.print(s);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(s));
	}

	@Override
	public void print(@Nullable String s) {
		super.print(s);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(s));
	}

	@Override
	public void print(@Nullable Object obj) {
		super.print(obj);
		super.flush();
		getOnWriteOccurred().accept(this, new ValuePrinted(obj));
	}

	@Override
	public void println() {
		super.println();
		super.flush();
		getOnWriteOccurred().accept(this, new NewlinePrinted());
	}

	@Override
	public void println(boolean x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(char x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(int x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(long x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(float x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(double x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(char[] x) {
		requireNonNull(x);

		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(@Nullable String x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	public void println(@Nullable Object x) {
		super.println(x);
		super.flush();
		getOnWriteOccurred().accept(this, new ValueWithNewlinePrinted(x));
	}

	@Override
	@NonNull
	public PrintWriter printf(@NonNull String format,
														@Nullable Object @Nullable ... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.printf(format, args);
		super.flush();

		@Nullable Object @NonNull [] normalizedArgs = args != null ? args : new Object[0];
		getOnWriteOccurred().accept(this, new PrintfPerformed(null, format, normalizedArgs));

		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter printf(@Nullable Locale l,
														@NonNull String format,
														@Nullable Object @Nullable ... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.printf(l, format, args);
		super.flush();
		getOnWriteOccurred().accept(this, new PrintfPerformed(l, format, args));
		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter format(@NonNull String format,
														@Nullable Object @Nullable ... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.format(format, args);
		super.flush();

		@Nullable Object @NonNull [] normalizedArgs = args != null ? args : new Object[0];
		getOnWriteOccurred().accept(this, new FormatPerformed(null, format, normalizedArgs));

		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter format(@Nullable Locale l,
														@NonNull String format,
														@Nullable Object @Nullable ... args) {
		requireNonNull(format);

		PrintWriter printWriter = super.format(l, format, args);
		super.flush();

		@Nullable Object @NonNull [] normalizedArgs = args != null ? args : new Object[0];
		getOnWriteOccurred().accept(this, new FormatPerformed(l, format, normalizedArgs));

		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter append(@Nullable CharSequence csq) {
		// JDK does this, we mirror it
		if (csq == null)
			csq = "null";

		PrintWriter printWriter = super.append(csq);
		super.flush();
		getOnWriteOccurred().accept(this, new CharSequenceAppended(csq, 0, csq.length()));
		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter append(@Nullable CharSequence csq,
														int start,
														int end) {
		// JDK does this, we mirror it
		if (csq == null)
			csq = "null";

		PrintWriter printWriter = super.append(csq, start, end);
		super.flush();
		getOnWriteOccurred().accept(this, new CharSequenceAppended(csq, start, end));
		return printWriter;
	}

	@Override
	@NonNull
	public PrintWriter append(char c) {
		PrintWriter printWriter = super.append(c);
		super.flush();
		getOnWriteOccurred().accept(this, new CharAppended(c));
		return printWriter;
	}

	@Override
	public void flush() {
		super.flush();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getOnWriteFinalized().accept(this);
		}
	}

	@Override
	public void close() {
		super.flush();
		super.close();

		if (!getWriteFinalized()) {
			setWriteFinalized(true);
			getOnWriteFinalized().accept(this);
		}
	}
}
