package dev.shayveri.core.ingress.asdb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Byte-level tests for the ABP/1 encoding.
 *
 * <p>WHY A GOLDEN FIXTURE AND NOT ONLY ROUND-TRIPS. Encoding and decoding in
 * the same file will agree with each other even when both are wrong. asdb's
 * {@code src/wire.rs} is the other half of this protocol and there is no shared
 * schema, no code generation and nothing that fails at compile time if the two
 * drift. The hex string in {@link #encodingMatchesTheRustSideByteForByte} was
 * produced by the Rust encoder, and the identical fixture is asserted on that
 * side, so either implementation changing its mind fails a test instead of
 * silently corrupting documents.
 *
 * <p>No server and no Spring context: this is arithmetic on byte arrays.
 */
class AbpCodecTest {

	/** Roughly what a TelemetrySnapshot serialises to, which is what this protocol carries all day. */
	private static Map<String, Object> telemetryDocument() {
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("kills", 7);

		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("placeId", "place-1");
		doc.put("playerCount", 42);
		doc.put("serverFps", 58.5d);
		doc.put("customMetrics", metrics);
		doc.put("receivedAt", 1754500000000L);
		return doc;
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return out.toString();
	}

	private static byte[] encode(Map<String, Object> doc) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		AbpCodec.putDocumentBody(out, doc);
		return out.toByteArray();
	}

	@Test
	@DisplayName("the encoding matches asdb's own, byte for byte")
	void encodingMatchesTheRustSideByteForByte() {
		/*
		 * Produced by asdb's put_document_body for the identical document.
		 * If this assertion fails, one of the two implementations changed and
		 * the other did not. Do not "fix" it by pasting in the new bytes
		 * without confirming which side is right.
		 */
		String fromRust = "050000000d000000637573746f6d4d657472696373070100000005000000"
				+ "6b696c6c73030700000000000000" + "07000000706c61636549640507000000706c6163652d31"
				+ "0b000000706c61796572436f756e74032a00000000000000"
				+ "0a0000007265636569766564417403006959809801000009000000"
				+ "736572766572467073040000000000404d40";

		assertEquals(fromRust, hex(encode(telemetryDocument())));
	}

	@Test
	@DisplayName("field order in the source map does not change the bytes")
	void encodingIsCanonical() {
		// asdb reads into a HashMap and discards order, so a stable encoding is
		// only worth anything if it is stable regardless of how the map was built.
		Map<String, Object> forwards = new LinkedHashMap<>();
		forwards.put("a", 1);
		forwards.put("b", 2);
		forwards.put("c", 3);

		Map<String, Object> backwards = new LinkedHashMap<>();
		backwards.put("c", 3);
		backwards.put("b", 2);
		backwards.put("a", 1);

		assertArrayEquals(encode(forwards), encode(backwards));
	}

	@Test
	@DisplayName("every value type survives a round trip")
	void roundTrip() {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("nul", null);
		doc.put("yes", true);
		doc.put("no", false);
		doc.put("int", 42L);
		doc.put("dbl", 58.5d);
		doc.put("str", "café ☕");
		doc.put("arr", List.of(1L, 2L));
		doc.put("nested", Map.of("k", 7L));

		Map<String, Object> back = new AbpCodec.Cursor(encode(doc)).documentBody();

		assertEquals(8, back.size());
		assertEquals(null, back.get("nul"));
		assertEquals(true, back.get("yes"));
		assertEquals(false, back.get("no"));
		assertEquals(42L, back.get("int"));
		assertEquals(58.5d, back.get("dbl"));
		assertEquals("café ☕", back.get("str"));
		assertEquals(List.of(1L, 2L), back.get("arr"));
		assertEquals(Map.of("k", 7L), back.get("nested"));
	}

	@Test
	@DisplayName("an Instant becomes epoch millis, matching the ASL path")
	void instantBecomesEpochMillis() {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("receivedAt", Instant.ofEpochMilli(1754500000000L));
		assertEquals(1754500000000L, new AbpCodec.Cursor(encode(doc)).documentBody().get("receivedAt"));
	}

	@Test
	@DisplayName("a long past 2^53 survives exactly")
	void largeLongIsExact() {
		// the precision the JSON path has to worry about is free here: eight
		// bytes in, eight bytes out, with no decimal rendering in between.
		long big = (1L << 53) + 1;
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("n", big);
		assertEquals(big, new AbpCodec.Cursor(encode(doc)).documentBody().get("n"));
	}

	@Test
	@DisplayName("non-finite doubles become null, as on the ASL path")
	void nonFiniteDoubles() {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("nan", Double.NaN);
		doc.put("inf", Double.POSITIVE_INFINITY);
		Map<String, Object> back = new AbpCodec.Cursor(encode(doc)).documentBody();
		assertEquals(null, back.get("nan"));
		assertEquals(null, back.get("inf"));
	}

	@Test
	@DisplayName("string lengths are byte counts, not char counts")
	void multiByteStringLength() {
		// an emoji is one char pair in Java and four bytes on the wire; getting
		// this wrong desynchronises every later field in the frame.
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("k", "😀");
		assertEquals("😀", new AbpCodec.Cursor(encode(doc)).documentBody().get("k"));
	}

	@Test
	@DisplayName("a hostile length is rejected rather than allocated")
	void hostileLengthIsRejected() {
		// a string claiming 2 GB inside a short payload must be an exception,
		// not an OutOfMemoryError that takes the application with it.
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(AbpCodec.TAG_STRING);
		AbpCodec.putU32(out, Integer.MAX_VALUE);
		out.writeBytes("abcd".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertThrows(AsdbClient.AsdbException.class, () -> new AbpCodec.Cursor(out.toByteArray()).value());
	}

	@Test
	@DisplayName("a truncated payload is an exception at every cut point")
	void truncationIsAlwaysAnException() {
		byte[] full = encode(telemetryDocument());
		for (int cut = 0; cut < full.length; cut++) {
			byte[] partial = java.util.Arrays.copyOf(full, cut);
			assertThrows(AsdbClient.AsdbException.class,
					() -> new AbpCodec.Cursor(partial).documentBody(),
					"a payload cut at " + cut + " bytes should not decode");
		}
	}

	@Test
	@DisplayName("an unknown tag is reported rather than guessed at")
	void unknownTagIsReported() {
		AsdbClient.AsdbException e = assertThrows(AsdbClient.AsdbException.class,
				() -> new AbpCodec.Cursor(new byte[] {0x7f}).value());
		assertTrue(e.getMessage().contains("7f"), e.getMessage());
	}

	@Test
	@DisplayName("a frame carries its own length, excluding the length field")
	void frameHeader() {
		byte[] frame = AbpCodec.frame(AbpCodec.OP_PING, new byte[0]);
		assertArrayEquals(new byte[] {1, 0, 0, 0, AbpCodec.OP_PING}, frame);
	}

	@Test
	@DisplayName("an insert payload names the collection then counts the documents")
	void insertPayloadShape() {
		byte[] payload = AbpCodec.insertPayload("game_events", List.of(Map.of("a", 1L), Map.of("b", 2L)));
		AbpCodec.Cursor c = new AbpCodec.Cursor(payload);
		assertEquals("game_events", c.string());
		assertEquals(2, c.u32());
		assertEquals(Map.of("a", 1L), c.documentBody());
		assertEquals(Map.of("b", 2L), c.documentBody());
	}

	@Test
	@DisplayName("a value that would be ASL syntax is just bytes here")
	void valuesCannotBecomeSyntax() {
		/*
		 * On the text path this payload is exactly what AsdbEntityMapper.quote
		 * exists to neutralise: it would close the string, close the document
		 * and append a delete stage. Here it is a length-prefixed byte run that
		 * is never lexed, so it comes back identical with no escaping involved.
		 * The defence is structural rather than procedural.
		 */
		String hostile = "x\" } | delete //";
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("placeId", hostile);
		// a field NAME that is a reserved word, which the text path must backtick
		doc.put("order", 1L);

		Map<String, Object> back = new AbpCodec.Cursor(encode(doc)).documentBody();
		assertEquals(hostile, back.get("placeId"));
		assertEquals(1L, back.get("order"));
	}
}
