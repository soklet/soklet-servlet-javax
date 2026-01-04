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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/*
 * Additional tests to cover {@link SokletServletPrintWriter}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class SokletServletPrintWriterTests {
	private static class RecordingListener {
		final List<SokletServletPrintWriterEvent> events = new ArrayList<>();
		int finalizedCount = 0;

		void onWrite(SokletServletPrintWriter writer, SokletServletPrintWriterEvent event) {
			events.add(event);
		}

		void onFinalized(SokletServletPrintWriter writer) {
			finalizedCount++;
		}
	}

	private SokletServletPrintWriter newWriter(StringWriter underlying, RecordingListener recordingListener) {
		return SokletServletPrintWriter
				.withWriter(underlying)
				.onWriteOccurred(recordingListener::onWrite)
				.onWriteFinalized(recordingListener::onFinalized)
				.build();
	}

	private static <T extends SokletServletPrintWriterEvent> List<T> eventsOfType(
			List<SokletServletPrintWriterEvent> events,
			Class<T> type) {
		List<T> result = new ArrayList<>();
		for (SokletServletPrintWriterEvent e : events) {
			if (type.isInstance(e)) {
				result.add(type.cast(e));
			}
		}
		return result;
	}

	@Test
	@DisplayName("write(char[], off, len) emits at least one CharsWritten and writes correct substring")
	void writeCharArrayWithOffsetEmitsCharsWritten() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		char[] buf = {'a', 'b', 'c', 'd', 'e'};
		writer.write(buf, 1, 3); // "bcd"

		assertEquals("bcd", underlying.toString());

		List<CharsWritten> charsEvents = eventsOfType(recording.events, CharsWritten.class);
		assertFalse(charsEvents.isEmpty(), "Expected at least one CharsWritten event");

		// Find one event matching the call we made
		CharsWritten match = charsEvents.stream()
				.filter(e -> e.offset() == 1 && e.length() == 3 && e.chars() == buf)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharsWritten with buf, offset=1, length=3"));

		assertSame(buf, match.chars());
		assertEquals(1, match.offset());
		assertEquals(3, match.length());
	}

	@Test
	@DisplayName("write(String, off, len) emits at least one StringWritten and writes correct substring")
	void writeStringWithOffsetEmitsStringWritten() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.write("hello world", 6, 5); // "world"

		assertEquals("world", underlying.toString());

		List<StringWritten> stringEvents = eventsOfType(recording.events, StringWritten.class);
		assertFalse(stringEvents.isEmpty(), "Expected at least one StringWritten event");

		StringWritten match = stringEvents.stream()
				.filter(e -> e.string().equals("hello world") && e.offset() == 6 && e.length() == 5)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected StringWritten with offset=6, length=5"));

		assertEquals("hello world", match.string());
		assertEquals(6, match.offset());
		assertEquals(5, match.length());
	}

	@Test
	@DisplayName("write(int) emits at least one CharWritten and writes character for code point")
	void writeIntEmitsCharWritten() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.write(65); // 'A'

		assertEquals("A", underlying.toString());

		List<CharWritten> charEvents = eventsOfType(recording.events, CharWritten.class);
		assertFalse(charEvents.isEmpty(), "Expected at least one CharWritten event");

		CharWritten match = charEvents.stream()
				.filter(e -> e.ch() == 65)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharWritten with ch=65"));

		assertEquals(65, match.ch());
	}

	@Test
	@DisplayName("print(int) emits at least one ValuePrinted and writes decimal representation")
	void printIntEmitsValuePrinted() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.print(42);

		assertEquals("42", underlying.toString());

		List<ValuePrinted> valueEvents = eventsOfType(recording.events, ValuePrinted.class);
		assertFalse(valueEvents.isEmpty(), "Expected at least one ValuePrinted event");

		ValuePrinted match = valueEvents.stream()
				.filter(e -> Integer.valueOf(42).equals(e.value()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected ValuePrinted with value=42"));

		assertEquals(42, match.value());
	}

	@Test
	@DisplayName("println(Object) emits at least one ValueWithNewlinePrinted and writes value + newline")
	void printlnObjectEmitsValueWithNewlinePrinted() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.println("test");

		assertEquals("test" + System.lineSeparator(), underlying.toString());

		List<ValueWithNewlinePrinted> events = eventsOfType(recording.events, ValueWithNewlinePrinted.class);
		assertFalse(events.isEmpty(), "Expected at least one ValueWithNewlinePrinted event");

		ValueWithNewlinePrinted match = events.stream()
				.filter(e -> "test".equals(e.value()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected ValueWithNewlinePrinted with value=\"test\""));

		assertEquals("test", match.value());
	}

	@Test
	@DisplayName("println() emits NewlinePrinted and writes just a newline")
	void printlnNoArgEmitsNewlinePrinted() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.println();

		assertEquals(System.lineSeparator(), underlying.toString());

		List<NewlinePrinted> events = eventsOfType(recording.events, NewlinePrinted.class);
		assertFalse(events.isEmpty(), "Expected at least one NewlinePrinted event");
	}

	@Test
	@DisplayName("printf(String, Object...) emits at least one PrintfPerformed with null locale")
	void printfEmitsPrintfPerformed() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.printf("Hi %s", "Mark");

		assertEquals("Hi Mark", underlying.toString());

		List<PrintfPerformed> printfEvents = eventsOfType(recording.events, PrintfPerformed.class);
		assertFalse(printfEvents.isEmpty(), "Expected at least one PrintfPerformed event");

		PrintfPerformed pf = printfEvents.get(0); // if multiple, just inspect the first
		assertNull(pf.locale());
		assertEquals("Hi %s", pf.format());
		// args may be nullable by design; if you've normalized to [], relax this accordingly
		assertNotNull(pf.args());
		assertEquals(1, pf.args().length);
		assertEquals("Mark", pf.args()[0]);
	}

	@Test
	@DisplayName("format(Locale, String, Object...) emits at least one FormatPerformed with locale")
	void formatWithLocaleEmitsFormatPerformed() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		Locale locale = Locale.GERMANY;
		writer.format(locale, "Value: %d", 10);

		assertEquals("Value: 10", underlying.toString());

		List<FormatPerformed> formatEvents = eventsOfType(recording.events, FormatPerformed.class);
		assertFalse(formatEvents.isEmpty(), "Expected at least one FormatPerformed event");

		FormatPerformed fp = formatEvents.get(0);
		assertEquals(locale, fp.locale());
		assertEquals("Value: %d", fp.format());
		assertNotNull(fp.args());
		assertEquals(1, fp.args().length);
		assertEquals(10, fp.args()[0]);
	}

	@Test
	@DisplayName("append(CharSequence) emits at least one CharSequenceAppended for full range")
	void appendCharSequenceEmitsCharSequenceAppended() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.append("abc");

		assertEquals("abc", underlying.toString());

		List<CharSequenceAppended> events = eventsOfType(recording.events, CharSequenceAppended.class);
		assertFalse(events.isEmpty(), "Expected at least one CharSequenceAppended event");

		CharSequenceAppended match = events.stream()
				.filter(e -> "abc".contentEquals(e.charSequence())
						&& e.start() == 0
						&& e.end() == 3)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharSequenceAppended covering full \"abc\""));

		assertEquals("abc", match.charSequence().toString());
		assertEquals(0, match.start());
		assertEquals(3, match.end());
	}

	@Test
	@DisplayName("append(null) appends \"null\" and emits CharSequenceAppended with \"null\"")
	void appendNullCharSequenceEmitsCharSequenceAppendedForLiteralNull() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.append((CharSequence) null);

		// JDK semantics: append(null) writes "null"
		assertEquals("null", underlying.toString());

		List<CharSequenceAppended> events = eventsOfType(recording.events, CharSequenceAppended.class);
		assertFalse(events.isEmpty(), "Expected at least one CharSequenceAppended event for null");

		CharSequenceAppended match = events.stream()
				.filter(e -> "null".contentEquals(e.charSequence())
						&& e.start() == 0
						&& e.end() == 4)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharSequenceAppended for \"null\""));

		assertEquals("null", match.charSequence().toString());
		assertEquals(0, match.start());
		assertEquals(4, match.end());
	}

	@Test
	@DisplayName("append(CharSequence, start, end) emits at least one CharSequenceAppended with same range")
	void appendCharSequenceRangeEmitsCharSequenceAppendedRange() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.append("abcdef", 2, 5); // "cde"

		assertEquals("cde", underlying.toString());

		List<CharSequenceAppended> events = eventsOfType(recording.events, CharSequenceAppended.class);
		assertFalse(events.isEmpty(), "Expected at least one CharSequenceAppended event");

		CharSequenceAppended match = events.stream()
				.filter(e -> "abcdef".contentEquals(e.charSequence())
						&& e.start() == 2
						&& e.end() == 5)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharSequenceAppended for range [2,5) of \"abcdef\""));

		assertEquals("abcdef", match.charSequence().toString());
		assertEquals(2, match.start());
		assertEquals(5, match.end());
	}

	@Test
	@DisplayName("append(char) emits at least one CharAppended and appends the character")
	void appendCharEmitsCharAppended() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.append('X');

		assertEquals("X", underlying.toString());

		List<CharAppended> events = eventsOfType(recording.events, CharAppended.class);
		assertFalse(events.isEmpty(), "Expected at least one CharAppended event");

		CharAppended match = events.stream()
				.filter(e -> e.ch() == 'X')
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected CharAppended with ch='X'"));

		assertEquals('X', match.ch());
	}

	@Test
	@DisplayName("flush triggers onWriteFinalized exactly once and does not require events")
	void flushTriggersFinalizationOnce() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		assertEquals(0, recording.finalizedCount);

		writer.flush();
		assertEquals(1, recording.finalizedCount);

		writer.flush();
		assertEquals(1, recording.finalizedCount); // still 1, no double-finalization
	}

	@Test
	@DisplayName("close triggers onWriteFinalized once and subsequent flush/close do not re-trigger")
	void closeTriggersFinalizationOnce() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.close();
		assertEquals(1, recording.finalizedCount);

		// Subsequent calls shouldn't re-trigger
		writer.flush();
		writer.close();
		assertEquals(1, recording.finalizedCount);
	}

	@Test
	@DisplayName("printf with null args treats args as empty")
	void printfWithNullArgsTreatsArgsAsEmpty() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.printf("hello", (Object[]) null);

		assertEquals("hello", underlying.toString());

		List<PrintfPerformed> events = eventsOfType(recording.events, PrintfPerformed.class);
		assertFalse(events.isEmpty(), "Expected at least one PrintfPerformed event");
		assertEquals(0, events.get(0).args().length);
	}

	@Test
	@DisplayName("format with null args treats args as empty")
	void formatWithNullArgsTreatsArgsAsEmpty() {
		StringWriter underlying = new StringWriter();
		RecordingListener recording = new RecordingListener();
		SokletServletPrintWriter writer = newWriter(underlying, recording);

		writer.format(Locale.US, "hello", (Object[]) null);

		assertEquals("hello", underlying.toString());

		List<FormatPerformed> events = eventsOfType(recording.events, FormatPerformed.class);
		assertFalse(events.isEmpty(), "Expected at least one FormatPerformed event");
		assertEquals(0, events.get(0).args().length);
	}
}
