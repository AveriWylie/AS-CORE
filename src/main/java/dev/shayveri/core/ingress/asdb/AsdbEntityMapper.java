package dev.shayveri.core.ingress.asdb;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Turns an annotated entity into an ASL insert statement.
 *
 * <p>THIS IS THE PIECE THAT MAKES THE SWAP INVISIBLE. The entity classes keep
 * their Spring Data annotations exactly as they are. Nothing in
 * {@code TelemetrySnapshot} or {@code GameEvent} changes, and neither does
 * {@code TelemetryService} above them. This class reads the same annotations
 * Spring Data reads and emits ASL instead of BSON.
 *
 * <p>WHICH ANNOTATIONS ARE HONOURED, and how they translate:
 *
 * <pre>
 *   {@literal @}Document("game_events")   the collection name
 *   {@literal @}Id                        an ordinary field; see the id note below
 *   {@literal @}Indexed                   NOT read here. Indexes are DDL, created once at
 *                             startup, not on every insert. See
 *                             AsdbTelemetryStore.
 *   {@literal @}Indexed(expireAfter)      NOT read here either, and this is the one that
 *                             does not map cleanly. asdb has no TTL index; the
 *                             server runs a sweeper instead, configured with
 *                             --ttl on its command line. The annotation is
 *                             therefore documentation on the Java side rather
 *                             than something enforced from it. That gap is
 *                             real and is called out in the store.
 * </pre>
 *
 * <p>THE ID FIELD. Mongo generates an ObjectId when {@code @Id} is left null.
 * asdb has no such notion, so a null id is simply omitted from the document
 * rather than fabricated. Documents are addressed by their storage DocId
 * internally, and nothing in the ingress path reads the id back, so inventing a
 * UUID here would add a field nobody uses. If a read path later needs stable
 * public ids, generate them in the service layer where the choice is visible,
 * not hidden in a mapper.
 *
 * <p>TYPE MAPPING. asdb's value model is int, float, string, bool, null, array,
 * document. Anything Java-specific has to be projected onto that:
 *
 * <pre>
 *   String                 -&gt; string, escaped
 *   Integer, Long, Short   -&gt; int
 *   Double, Float          -&gt; float
 *   Boolean                -&gt; bool
 *   Instant                -&gt; int, epoch millis    (see below)
 *   Map                    -&gt; document literal
 *   Collection, array      -&gt; array literal
 *   null                   -&gt; null
 *   anything else          -&gt; reflected over field by field
 * </pre>
 *
 * <p>INSTANT BECOMES EPOCH MILLIS, not a formatted string. asdb has no date
 * type, and a string timestamp would sort lexically, which is wrong the moment
 * a format changes, and could not be range-scanned by the TTL sweeper. Millis
 * as an int sorts correctly, compares correctly, and is what the sweeper's
 * {@code where receivedAt &lt; cutoff} needs. The cost is that the value is opaque
 * when you read the raw collection.
 */
public final class AsdbEntityMapper {

	private AsdbEntityMapper() { }

	// The collection an entity class maps to, from {@code @Document}
	public static String collectionOf(Class<?> type) {

		Document annotation = type.getAnnotation(Document.class);

		if (annotation == null) {
			throw new IllegalArgumentException(type.getSimpleName() + " has no @Document annotation, so it has no collection name");
		}

		// Spring Data allows either @Document("name") or @Document(collection = "name")
		String name = annotation.value().isEmpty() ? annotation.collection() : annotation.value();

		if (name.isEmpty()) {
			// Spring Data's own fallback: the uncapitalised simple class name
			String simple = type.getSimpleName();
			return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
		}

		return name;
	}

	/** Field names carrying a plain {@code @Indexed}, so indexes can be created at startup. */
	public static List<String> indexedFieldsOf(Class<?> type) {
		return java.util.Arrays.stream(type.getDeclaredFields())
				.filter(f -> f.isAnnotationPresent(org.springframework.data.mongodb.core.index.Indexed.class))
				.map(Field::getName)
				.toList();
	}

	/** {@code from <collection> | insert { ... }} for one entity. */
	public static String insertStatement(Object entity) {
		String collection = collectionOf(entity.getClass());
		return "from " + collection + " | insert " + documentLiteral(entity);
	}

	/**
	 * One statement inserting many entities of the same type.
	 *
	 * <p>Batched into a single request rather than one per document, because the
	 * asdb server serializes every statement behind a mutex: a hundred separate
	 * inserts means a hundred round trips each queueing for the same lock, while
	 * one statement takes it once. That matters for {@code saveEvents}, which
	 * receives a whole batch.
	 */
	public static String insertStatement(List<?> entities) {

		if (entities.isEmpty()) {
			throw new IllegalArgumentException("no entities to insert");
		}

		return insertStatement(collectionOf(entities.get(0).getClass()), entities);
	}

	/**
	 * The same statement with the collection given explicitly.
	 *
	 * <p>Exists so the text path can honour a caller-supplied collection the way
	 * the binary path always has. Without it the two transports had different
	 * behaviour behind one interface: {@code OP_INSERT} names its collection on
	 * the wire, while the text path derived it from {@code @Document} and
	 * silently ignored whatever it was handed. Harmless in production, since the
	 * store passes the annotation-derived name anyway, but a signature that
	 * promises something one implementation ignores is a trap for the next
	 * caller. A parity test found it, which is the argument for having one.
	 */
	public static String insertStatement(String collection, List<?> entities) {

		if (entities.isEmpty()) {
			throw new IllegalArgumentException("no entities to insert");
		}

		// Batch insert is BRACKETED: insert [ {...}, {...} ]. The unbracketed
		// comma form parses as a single document followed by trailing tokens,
		// which fails. asl.txt spells this out; it is worth restating because
		// the two forms look interchangeable and only one is.
		StringBuilder out = new StringBuilder("from ").append(collection).append(" | insert [");

		for (int i = 0; i < entities.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(documentLiteral(entities.get(i)));
		}

		return out.append("]").toString();
	}

	/**
	 * An entity as a field map, by reflection over its declared fields.
	 *
	 * <p>This is {@link #documentLiteral} with the text rendering removed. The
	 * binary protocol needs the same fields under the same names but must NOT
	 * have them turned into ASL syntax first, since the whole point of
	 * {@code OP_INSERT} is that values never become syntax. Both paths share
	 * this traversal so a field skipped by one cannot be included by the other.
	 *
	 * <p>LinkedHashMap rather than HashMap: declaration order is stable, which
	 * keeps the encoded bytes stable, which is what lets a test assert on them.
	 * asdb sorts keys on its own way out, so nothing downstream depends on this
	 * order; it exists to make the encoding reproducible on this side.
	 */
	public static java.util.LinkedHashMap<String, Object> toMap(Object entity) {

		java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();

		for (Field field : entity.getClass().getDeclaredFields()) {
			// static and synthetic fields are not data: the latter appear on
			// inner classes and in coverage-instrumented builds ($jacocoData).
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
				continue;
			}

			field.setAccessible(true);
			Object value;

			try {
				value = field.get(entity);
			} catch (IllegalAccessException e) {
				throw new AsdbClient.AsdbException("cannot read field " + field.getName(), e);
			}

			// A null @Id is omitted rather than written as null, matching the
			// text path: asdb generates no ids, so the field would be dead weight.
			if (value == null && field.isAnnotationPresent(Id.class)) {
				continue;
			}

			map.put(field.getName(), value);
		}

		return map;
	}

	/** An entity as an ASL document literal, by reflection over its declared fields. */
	public static String documentLiteral(Object entity) {

		StringBuilder out = new StringBuilder("{ ");
		boolean first = true;

		for (Field field : entity.getClass().getDeclaredFields()) {
			// static and synthetic fields are not data: the latter appear on
			// inner classes and in coverage-instrumented builds ($jacocoData).
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
				continue;
			}

			field.setAccessible(true);
			Object value;

			try {
				value = field.get(entity);
			} catch (IllegalAccessException e) {
				throw new AsdbClient.AsdbException("cannot read field " + field.getName(), e);
			}

			// A null @Id is omitted rather than written as null; see the class note.
			if (value == null && field.isAnnotationPresent(Id.class)) {
				continue;
			}

			if (!first) {
				out.append(", ");
			}

			first = false;
			out.append(field.getName()).append(": ").append(literal(value));

		}

		return out.append(" }").toString();
	}

	/**
	 * One Java value as an ASL literal.
	 *
	 * <p>Package-private rather than public so the escaping rules stay behind
	 * this class: every caller should be going through {@code insertStatement}.
	 */
	static String literal(Object value) {

		if (value == null) {
			return "null";
		}

		if (value instanceof String s) {
			return quote(s);
		}

		if (value instanceof Instant instant) {
			return Long.toString(instant.toEpochMilli()); // see the class note
		}

		if (value instanceof Boolean b) {
			return b ? "true" : "false";
		}

		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
			return value.toString();
		}

		if (value instanceof Double || value instanceof Float) {
			double d = ((Number) value).doubleValue();
			// asdb's JSON writer maps non-finite floats to null, and ASL has no
			// literal for them either. Normalising here means the value that
			// lands is the one this side chose, not a surprise downstream.
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				return "null";
			}
			return value.toString();
		}

		if (value instanceof Map<?, ?> map) {
			StringBuilder out = new StringBuilder("{ ");
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) {
					out.append(", ");
				}
				first = false;
				// Map keys arrive as arbitrary strings (customMetrics is a
				// Map<String, Object> filled from user JSON), so a key can
				// collide with an ASL keyword or contain punctuation. Backtick
				// quoting is what makes that safe.
				out.append(backtick(String.valueOf(entry.getKey())))
						.append(": ")
						.append(literal(entry.getValue()));
			}
			return out.append(" }").toString();
		}

		if (value instanceof Collection<?> collection) {
			StringBuilder out = new StringBuilder("[");
			boolean first = true;
			for (Object item : collection) {
				if (!first) {
					out.append(", ");
				}
				first = false;
				out.append(literal(item));
			}
			return out.append("]").toString();
		}

		if (value.getClass().isArray()) {
			int length = java.lang.reflect.Array.getLength(value);
			StringBuilder out = new StringBuilder("[");
			for (int i = 0; i < length; i++) {
				if (i > 0) {
					out.append(", ");
				}
				out.append(literal(java.lang.reflect.Array.get(value, i)));
			}
			return out.append("]").toString();
		}

		// A nested object such as GameEventRequest.Position. Reflected over the
		// same way as the top-level entity, which is what makes records work
		// without a special case.
		return documentLiteral(value);
	}

	/**
	 * An ASL string literal.
	 *
	 * <p>THIS IS AN INJECTION BOUNDARY, and it is the reason this method exists
	 * rather than string concatenation at the call sites. Telemetry field values
	 * come from Roblox game servers, which is to say from outside. A placeId of
	 * <pre>x" } | delete //</pre> concatenated raw would end the string, close the
	 * document, and start a new stage. Same class of bug as SQL injection, same
	 * fix: escape at the single point where untrusted text becomes syntax.
	 *
	 * <p>Escapes exactly what asdb's lexer recognises: quote, backslash, newline,
	 * tab, carriage return. Any other control character is dropped rather than
	 * passed through, because the lexer has no escape for it and would otherwise
	 * see a raw byte inside a string literal.
	 */
	static String quote(String raw) {

		StringBuilder out = new StringBuilder(raw.length() + 2).append('"');

		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\t' -> out.append("\\t");
				case '\r' -> out.append("\\r");
				// below 0x20 and not one of the above: dropped, see above
				default -> {if (c >= 0x20) {out.append(c);}
				}
			}
		}

		return out.append('"').toString();
	}

	/**
	 * A field name, backtick-quoted when it needs to be.
	 *
	 * <p>Bare identifiers are left bare so the generated ASL stays readable in
	 * logs. A name that is not a plain identifier gets backticks, which is
	 * ASL v0.2's escape for exactly this. Embedded backticks are stripped: they
	 * have no escape inside a quoted identifier, and a metric key containing one
	 * is not worth failing an entire telemetry batch over.
	 */
	static String backtick(String name) {

		boolean plain = !name.isEmpty() && (Character.isLetter(name.charAt(0)) || name.charAt(0) == '_');

		for (int i = 0; plain && i < name.length(); i++) {
			char c = name.charAt(i);
			plain = Character.isLetterOrDigit(c) || c == '_';
		}

		// A reserved word is syntactically a plain identifier and still has to
		// be quoted: asdb's lexer maps "order" to a stage-keyword token before
		// the parser ever sees it, so { order: 1 } is a parse error.
		if (plain && RESERVED.contains(name)) {
			plain = false;
		}

		return plain ? name : "`" + name.replace("`", "") + "`";
	}

	/**
	 * Words asdb's lexer turns into keyword tokens rather than identifiers.
	 *
	 * <p>Kept as an explicit set rather than derived, because it has to match
	 * the lexer's own table and there is no way to ask the server for it. If
	 * asdb adds a keyword, this list has to follow, and a stale entry here only
	 * causes an unnecessary backtick rather than a failure, which is the safe
	 * direction to be wrong in.
	 */
	private static final java.util.Set<String> RESERVED = java.util.Set.of(
			"from", "where", "select", "order", "limit", "offset", "group", "join",
			"insert", "update", "delete", "drop", "create", "index", "on", "set",
			"as", "in", "exists", "missing", "contains", "starts", "ends", "use",
			"asc", "desc", "and", "or", "not", "if", "then", "else",
			"true", "false", "null", "required", "unique", "any");
}
