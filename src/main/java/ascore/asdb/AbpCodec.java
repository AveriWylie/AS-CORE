package ascore.asdb;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ABP/1 wire encoding, the Java half.
 *
 * <p>THIS FILE IS A MIRROR. Its counterpart is {@code src/wire.rs} in asdb, and
 * the two have to agree byte for byte. Tag values and opcodes are repeated here
 * rather than derived, because there is no shared schema and nothing would
 * catch a drift at compile time. {@code AbpCodecTest} encodes fixtures whose
 * exact bytes are asserted, so a change on either side fails a test rather than
 * corrupting data.
 *
 * <p>WHY BINARY AT ALL. Over HTTP, 90% of an insert was protocol: 61.88us of a
 * 68.82us request, half of it opening and closing a TCP connection the previous
 * request had just discarded. On the binary path with requests pipelined that
 * falls to 0.12us of 7.07us, which is 2%. asdb's PROTOCOL.txt has the full
 * table and the method.
 *
 * <p>WHAT THIS BUYS OVER {@link AsdbEntityMapper}, beyond speed: values are
 * written as length-prefixed byte runs and are never lexed, so the escaping
 * that {@code AsdbEntityMapper.quote} has to get right every single time has
 * nothing to get wrong here. A placeId of {@code x" } | delete //} is data,
 * structurally, not because a routine remembered to escape it.
 *
 * <p>Everything is little-endian, matching asdb's storage layer.
 */
final class AbpCodec {

	private AbpCodec() { }
	// requests
	static final byte OP_EXEC = 0x01;
	static final byte OP_INSERT = 0x02;
	static final byte OP_PING = 0x03;
	static final byte OP_CLOSE = 0x04;
	// responses
	static final byte OP_AFFECTED = (byte) 0x81;
	static final byte OP_DOCUMENTS = (byte) 0x82;
	static final byte OP_ERROR = (byte) 0x83;
	static final byte OP_PONG = (byte) 0x84;
	// value tags
	static final byte TAG_NULL = 0x00;
	static final byte TAG_FALSE = 0x01;
	static final byte TAG_TRUE = 0x02;
	static final byte TAG_INT = 0x03;
	static final byte TAG_FLOAT = 0x04;
	static final byte TAG_STRING = 0x05;
	static final byte TAG_ARRAY = 0x06;
	static final byte TAG_DOCUMENT = 0x07;

	/** Matches MAX_FRAME in wire.rs. A larger reply is a bug or an attack, not a document. */
	static final int MAX_FRAME = 64 * 1024 * 1024;

	/* ---------- writing ---------- */

	static void putU32(ByteArrayOutputStream out, int n) {
		out.write(n);
		out.write(n >>> 8);
		out.write(n >>> 16);
		out.write(n >>> 24);
	}

	static void putI64(ByteArrayOutputStream out, long n) {
		for (int i = 0; i < 8; i++) {
			out.write((int) (n >>> (8 * i)));
		}
	}

	static void putString(ByteArrayOutputStream out, String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		// the length is the BYTE count, not the char count: a string of emoji
		// has more bytes than chars and the Rust side reads bytes.
		putU32(out, bytes.length);
		out.writeBytes(bytes);
	}

	/**
	 * One Java value as an ABP value.
	 *
	 * <p>The type mapping is the same one {@link AsdbEntityMapper} applies, so
	 * the two paths store identical documents. {@code Instant} becomes epoch
	 * millis for the same reason it does there: asdb has no date type, and a
	 * string timestamp would sort lexically and could not be range-scanned by
	 * the TTL sweeper.
	 */
	static void putValue(ByteArrayOutputStream out, Object value) {

		if (value == null) {
			out.write(TAG_NULL);
			return;
		}

		if (value instanceof Boolean b) {
			out.write(b ? TAG_TRUE : TAG_FALSE);
			return;
		}

		if (value instanceof Instant instant) {
			out.write(TAG_INT);
			putI64(out, instant.toEpochMilli());
			return;
		}

		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
			out.write(TAG_INT);
			putI64(out, ((Number) value).longValue());
			return;
		}

		if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			// asdb has no literal for NaN or infinity on either path, so both
			// normalise to null here rather than becoming a surprise downstream.
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				out.write(TAG_NULL);
				return;
			}
			out.write(TAG_FLOAT);
			putI64(out, Double.doubleToLongBits(d));
			return;
		}

		if (value instanceof String s) {
			out.write(TAG_STRING);
			putString(out, s);
			return;
		}

		if (value instanceof Map<?, ?> map) {
			out.write(TAG_DOCUMENT);
			putDocumentBody(out, map);
			return;
		}

		if (value instanceof Collection<?> items) {
			out.write(TAG_ARRAY);
			putU32(out, items.size());
			for (Object item : items) {
				putValue(out, item);
			}
			return;
		}

		if (value.getClass().isArray()) {
			int length = java.lang.reflect.Array.getLength(value);
			out.write(TAG_ARRAY);
			putU32(out, length);
			for (int i = 0; i < length; i++) {
				putValue(out, java.lang.reflect.Array.get(value, i));
			}
			return;
		}

		// A nested object such as GameEventRequest.Position, reflected the same
		// way the top-level entity is. This is what makes records work with no
		// special case.
		out.write(TAG_DOCUMENT);
		putDocumentBody(out, AsdbEntityMapper.toMap(value));
	}

	/**
	 * A document body: field count, then each key and value. No tag byte.
	 *
	 * <p>KEYS ARE SORTED, matching {@code put_document_body} in wire.rs.
	 *
	 * <p>The server does not need this: it reads into a HashMap and discards
	 * the order. What sorting buys is that ONE logical document has ONE byte
	 * encoding, no matter which side or which language produced it. That is
	 * what makes {@code AbpCodecTest.encodingMatchesTheRustSideByteForByte}
	 * possible, and a byte-exact fixture shared by both implementations is the
	 * only thing that catches the two drifting apart, since nothing else here
	 * is checked at compile time.
	 *
	 * <p>The cost is one sort of roughly seven keys per document, against a
	 * measured 1.55us of storage work per document. It does not show up.
	 */
	static void putDocumentBody(ByteArrayOutputStream out, Map<?, ?> doc) {

		// The ENTRIES are sorted, not the keys with a lookup afterwards: a map
		// with non-String keys (customMetrics is filled from user JSON and is
		// only String-keyed by convention) would look up a stringified key that
		// is not in the map and silently write null for every field.
		List<Map.Entry<?, ?>> entries = new ArrayList<>(doc.size());

		for (Map.Entry<?, ?> entry : doc.entrySet()) {
			entries.add(entry);
		}

		entries.sort(java.util.Comparator.comparing(e -> String.valueOf(e.getKey())));

		putU32(out, entries.size());

		for (Map.Entry<?, ?> entry : entries) {
			putString(out, String.valueOf(entry.getKey()));
			putValue(out, entry.getValue());
		}
	}

	/** A complete frame: length, opcode, payload. */
	static byte[] frame(byte opcode, byte[] payload) {
		byte[] out = new byte[payload.length + 5];
		int len = payload.length + 1;
		out[0] = (byte) len;
		out[1] = (byte) (len >>> 8);
		out[2] = (byte) (len >>> 16);
		out[3] = (byte) (len >>> 24);
		out[4] = opcode;
		System.arraycopy(payload, 0, out, 5, payload.length);
		return out;
	}

	/** An OP_INSERT payload: collection, count, then the document bodies. */
	static byte[] insertPayload(String collection, List<? extends Map<String, Object>> docs) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(256 * Math.max(1, docs.size()));
		putString(out, collection);
		putU32(out, docs.size());
		for (Map<String, Object> doc : docs) {
			putDocumentBody(out, doc);
		}
		return out.toByteArray();
	}

	/** An OP_EXEC payload: one length-prefixed ASL statement. */
	static byte[] execPayload(String statement) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(statement.length() + 8);
		putString(out, statement);
		return out.toByteArray();
	}

	/* ---------- reading ---------- */

	/** A decoded reply frame. */
	record Reply(byte opcode, byte[] payload) {

		long affected() {
			long n = 0;
			for (int i = 7; i >= 0; i--) {
				n = (n << 8) | (payload[i] & 0xffL);
			}
			return n;
		}

		String error() {
			return new Cursor(payload).string();
		}

		List<Map<String, Object>> documents() {
			Cursor c = new Cursor(payload);
			int n = c.u32();
			List<Map<String, Object>> docs = new ArrayList<>(Math.min(n, 1024));
			for (int i = 0; i < n; i++) {
				docs.add(c.documentBody());
			}
			return docs;
		}
	}

	/**
	 * Reads one frame.
	 *
	 * <p>The length is validated against {@link #MAX_FRAME} BEFORE allocating.
	 * A corrupt or hostile header claiming 4 GB must be an exception, not an
	 * OutOfMemoryError that takes the application down with it.
	 */
	static Reply readFrame(DataInputStream in) throws IOException {

		int len = Integer.reverseBytes(in.readInt());

		if (len <= 0 || len > MAX_FRAME) {
			throw new IOException("asdb sent a frame length of " + len + ", which is not a frame");
		}

		byte[] buf = new byte[len];
		in.readFully(buf);
		byte[] payload = new byte[len - 1];
		System.arraycopy(buf, 1, payload, 0, len - 1);
		return new Reply(buf[0], payload);
	}

	/** A position in a payload. Package-private so tests can decode fixtures. */
	static final class Cursor {

		private final byte[] buf;
		private int pos;

		Cursor(byte[] buf) {this.buf = buf;}

		/*
		Bounds check written as a SUBTRACTION, not pos + n > buf.length.

		The addition overflows: a length field of Integer.MAX_VALUE makes
		pos + n wrap negative, the check passes, and the read blows up with a
		raw StringIndexOutOfBoundsException instead of a diagnosable error.
		That is precisely the hostile input this method exists to stop, and it
		was caught by hostileLengthIsRejected rather than by inspection.
		*/
		private void need(int n) {
			if (n < 0 || n > buf.length - pos) {
				throw new AsdbClient.AsdbException("asdb frame ended mid-value");
			}
		}

		int u32() {
			need(4);
			int n = (buf[pos] & 0xff) | (buf[pos + 1] & 0xff) << 8 | (buf[pos + 2] & 0xff) << 16 | (buf[pos + 3] & 0xff) << 24;
			pos += 4;
			return n;
		}

		long i64() {
			need(8);
			long n = 0;
			for (int i = 7; i >= 0; i--) {
				n = (n << 8) | (buf[pos + i] & 0xffL);
			}
			pos += 8;
			return n;
		}

		String string() {
			int n = u32();
			// a length past 2^31 arrives as a negative int; need() rejects it
			need(n);
			String s = new String(buf, pos, n, StandardCharsets.UTF_8);
			pos += n;
			return s;
		}

		Object value() {
			need(1);
			byte tag = buf[pos++];
			return switch (tag) {
				case TAG_NULL -> null;
				case TAG_FALSE -> Boolean.FALSE;
				case TAG_TRUE -> Boolean.TRUE;
				case TAG_INT -> i64();
				case TAG_FLOAT -> Double.longBitsToDouble(i64());
				case TAG_STRING -> string();
				case TAG_ARRAY -> {
					int n = u32();
					// each value costs at least its tag byte, so a count larger
					// than the bytes remaining cannot be real
					if (n < 0 || n > buf.length - pos) {
						throw new AsdbClient.AsdbException("asdb sent an array count of " + n);
					}
					List<Object> items = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						items.add(value());
					}
					yield items;
				}
				case TAG_DOCUMENT -> documentBody();
				default -> throw new AsdbClient.AsdbException("unknown asdb value tag 0x" + Integer.toHexString(tag & 0xff));
			};
		}

		Map<String, Object> documentBody() {

			int n = u32();

			if (n < 0 || n > buf.length - pos) {
				throw new AsdbClient.AsdbException("asdb sent a field count of " + n);
			}

			Map<String, Object> doc = new LinkedHashMap<>(Math.max(4, n * 2));

			for (int i = 0; i < n; i++) {
				doc.put(string(), value());
			}

			return doc;
		}
	}
}
