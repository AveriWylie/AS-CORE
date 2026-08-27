package dev.shayveri.core.ingress.asdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.shayveri.core.ingress.GameEvent;
import dev.shayveri.core.ingress.TelemetrySnapshot;

/**
 * Unit tests for the annotation-to-ASL mapping.
 *
 * <p>These are pure string tests: no server, no Spring context, nothing to
 * start. That is the point of keeping the mapper static and side-effect free.
 * The end-to-end check that the emitted ASL actually parses lives separately;
 * this file is about the translation rules being right.
 */
class AsdbEntityMapperTest {

	private static TelemetrySnapshot snapshot() {
		return new TelemetrySnapshot(
				"place-1",
				"job-a",
				42,
				58.5,
				"round-3",
				Map.of("kills", 7),
				Instant.ofEpochMilli(1_754_500_000_000L));
	}

	@Test
	@DisplayName("@Document supplies the collection name")
	void collectionComesFromTheAnnotation() {
		assertEquals("telemtry_snapshots", AsdbEntityMapper.collectionOf(TelemetrySnapshot.class));
		assertEquals("game_events", AsdbEntityMapper.collectionOf(GameEvent.class));
	}

	@Test
	@DisplayName("@Indexed fields are discoverable for startup DDL")
	void indexedFieldsAreFound() {
		// GameEvent marks placeId for heatmap queries
		assertTrue(AsdbEntityMapper.indexedFieldsOf(GameEvent.class).contains("placeId"));
	}

	@Test
	@DisplayName("Instant becomes epoch millis, not a formatted string")
	void instantBecomesEpochMillis() {
		String asl = AsdbEntityMapper.insertStatement(snapshot());
		assertTrue(asl.contains("receivedAt: 1754500000000"), asl);
		// a string timestamp would sort lexically and could not be range-scanned
		assertFalse(asl.contains("receivedAt: \""), asl);
	}

	@Test
	@DisplayName("a null @Id is omitted rather than written as null")
	void nullIdIsOmitted() {
		String asl = AsdbEntityMapper.insertStatement(snapshot());
		assertFalse(asl.contains("id:"), "asdb has no generated ids; the field should be absent: " + asl);
	}

	@Test
	@DisplayName("the statement targets the right collection and stage")
	void statementShape() {
		assertTrue(AsdbEntityMapper.insertStatement(snapshot()).startsWith("from telemtry_snapshots | insert { "));
	}

	@Test
	@DisplayName("scalars map onto asdb's value model")
	void scalarMapping() {
		assertEquals("null", AsdbEntityMapper.literal(null));
		assertEquals("true", AsdbEntityMapper.literal(Boolean.TRUE));
		assertEquals("42", AsdbEntityMapper.literal(42));
		assertEquals("42", AsdbEntityMapper.literal(42L));
		assertEquals("58.5", AsdbEntityMapper.literal(58.5));
		assertEquals("\"hi\"", AsdbEntityMapper.literal("hi"));
	}

	@Test
	@DisplayName("non-finite doubles become null, since ASL has no literal for them")
	void nonFiniteDoubles() {
		assertEquals("null", AsdbEntityMapper.literal(Double.NaN));
		assertEquals("null", AsdbEntityMapper.literal(Double.POSITIVE_INFINITY));
	}

	@Test
	@DisplayName("collections and maps become array and document literals")
	void compositeMapping() {
		assertEquals("[1, 2]", AsdbEntityMapper.literal(List.of(1, 2)));
		assertEquals("{ a: 1 }", AsdbEntityMapper.literal(Map.of("a", 1)));
	}

	// ---- the injection boundary ----

	@Test
	@DisplayName("a quote in user data cannot escape the string literal")
	void quotesAreEscaped() {
		assertEquals("\"a\\\"b\"", AsdbEntityMapper.quote("a\"b"));
	}

	@Test
	@DisplayName("an ASL injection attempt is neutralised")
	void injectionIsNeutralised() {
		/*
		 * Telemetry values come from Roblox game servers, which is to say from
		 * outside. Concatenated raw, this placeId would close the string, close
		 * the document, and append a delete stage. Same class of bug as SQL
		 * injection.
		 */
		String hostile = "x\" } | delete //";
		String literal = AsdbEntityMapper.quote(hostile);
		// exactly one opening and one closing quote: the payload's own quote is escaped
		assertTrue(literal.startsWith("\"") && literal.endsWith("\""), literal);
		assertTrue(literal.contains("\\\""), "the embedded quote must be escaped: " + literal);
		// and the delete stage is inert text inside the string, not syntax
		assertEquals("\"x\\\" } | delete //\"", literal);
	}

	@Test
	@DisplayName("backslashes and newlines are escaped, control bytes dropped")
	void otherEscapes() {
		assertEquals("\"a\\\\b\"", AsdbEntityMapper.quote("a\\b"));
		assertEquals("\"a\\nb\"", AsdbEntityMapper.quote("a\nb"));
		// asdb's lexer has no escape for a raw control byte inside a string
		assertEquals("\"ab\"", AsdbEntityMapper.quote("a\u0001b"));
	}

	@Test
	@DisplayName("metric keys that are not plain identifiers get backticked")
	void keysAreQuotedWhenTheyHaveTo() {
		// customMetrics is a Map<String, Object> filled from user JSON, so a key
		// can be anything at all, including an ASL keyword.
		assertEquals("kills", AsdbEntityMapper.backtick("kills"));
		assertEquals("`order`", AsdbEntityMapper.backtick("order"));
		assertEquals("`a-b`", AsdbEntityMapper.backtick("a-b"));
		assertEquals("`1st`", AsdbEntityMapper.backtick("1st"));
	}

	@Test
	@DisplayName("a batch is one statement, not one per document")
	void batchIsASingleStatement() {
		String asl = AsdbEntityMapper.insertStatement(List.of(snapshot(), snapshot()));
		assertEquals(1, asl.split("\\| insert").length - 1, "one insert stage: " + asl);
		// BRACKETED. The unbracketed comma form does not parse; asserted here
		// because a live server is the only other thing that would catch it.
		assertTrue(asl.contains("| insert ["), "batch must be bracketed: " + asl);
		assertTrue(asl.endsWith("]"), "batch must be bracketed: " + asl);
		assertTrue(asl.contains("}, {"), "documents separated inside the bracket: " + asl);
	}
}
