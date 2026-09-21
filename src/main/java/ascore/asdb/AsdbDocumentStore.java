package ascore.asdb;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Id;
import org.springframework.objenesis.ObjenesisStd;

/**
 * One entity type's collection in asdb, read and written as that entity. The asdb
 * adapters for nodes, jobs, config and audit are each a thin layer over one or two
 * of these, so mapping and escaping live here once. See
 * documentation/architecture/asdb.md, "The document store".
 *
 * WRITES go over the binary protocol where they can: an insert sends the document as
 * bytes and nothing is escaped. An update has no binary opcode yet, so it is ASL text
 * built through AsdbEntityMapper.literal, the same injection boundary the text path uses.
 *
 * READS come back as maps and are turned into the entity by field type: epoch millis
 * back to Instant, strings back to enums, and whole numbers narrowed to Integer where
 * they fit, which is what Jackson and Mongo hand back for the same values.
 *
 * Entities are built without calling a constructor, the way Spring Data does for a
 * class with no default one, then filled field by field.
 */
public final class AsdbDocumentStore<T> {

	private static final Logger log = LoggerFactory.getLogger(AsdbDocumentStore.class);
	private static final ObjenesisStd OBJENESIS = new ObjenesisStd(true);
	private final AsdbBinaryClient client;
	private final Class<T> type;
	private final String collection;
	private final Field id;

	public AsdbDocumentStore(AsdbBinaryClient client, Class<T> type) {
		this.client = client;
		this.type = type;
		this.collection = AsdbEntityMapper.collectionOf(type);
		this.id = idField(type);
	}

	/**
	 * Creates the collection and an index on the id and on every @Indexed field, plus any
	 * extra fields named. Same rule as AsdbTelemetryStore: "already exists" is the normal
	 * case after the first start, so failures are logged, not thrown.
	 */
	public void ensureSchema(String... extraIndexes) {

		if (!client.isHealthy()) {
			log.error("asdb is UNREACHABLE; {} reads and writes will fail until it is running", collection);
			return;
		}

		attempt("create " + collection + " {}");
		List<String> indexed = new ArrayList<>(AsdbEntityMapper.indexedFieldsOf(type));
		indexed.add(id.getName());
		indexed.addAll(List.of(extraIndexes));

		for (String field : indexed.stream().distinct().toList()) {
			attempt("create index on " + collection + "." + field);
		}
	}

	/**
	 * Inserts one entity. A null String id is filled with a UUID first, since asdb
	 * generates no ids and these stores read them back. Mongo's equivalent is its ObjectId.
	 */
	public T insert(T entity) {
		if (read(id, entity) == null && id.getType() == String.class) write(id, entity, UUID.randomUUID().toString());
		client.insert(collection, List.of(document(entity)));
		return entity;
	}

	// overwrites every field of the document with this entity's id, returning how many matched
	public long replace(T entity) {
		Map<String, Object> doc = document(entity);
		Object key = doc.remove(id.getName());
		String assignments = doc.entrySet().stream()
				.map(e -> AsdbEntityMapper.backtick(e.getKey()) + " = " + AsdbEntityMapper.literal(e.getValue()))
				.collect(Collectors.joining(", "));

		return client.execute("from " + collection + " where " + eq(id.getName(), key) + " update set " + assignments).affected();
	}

	/**
	 * Replace, or insert when nothing matched. asdb has no upsert, so this is two
	 * statements, and synchronized so two callers in this JVM cannot both insert the same
	 * id. Another process writing the same collection could still race it.
	 */
	public synchronized T upsert(T entity) {
		if (replace(entity) == 0) insert(entity);
		return entity;
	}

	public List<T> find() {return find("");}

	/**
	 * Every document matching the given ASL tail, such as "where x == 1 order y desc".
	 * Build any value in it with eq or in, never by concatenation: those are what escape it.
	 */
	public List<T> find(String tail) {
		String statement = tail.isBlank() ? "from " + collection : "from " + collection + " " + tail;
		return client.query(statement).stream().map(this::entity).toList();
	}

	public static String eq(String field, Object value) {
		return AsdbEntityMapper.backtick(field) + " == " + AsdbEntityMapper.literal(plain(value));
	}

	public static String in(String field, Collection<?> values) {
		return AsdbEntityMapper.backtick(field) + " in " +
				AsdbEntityMapper.literal(values.stream().map(AsdbDocumentStore::plain).toList());
	}

	public static String atLeast(String field, Object value) {
		return AsdbEntityMapper.backtick(field) + " >= " + AsdbEntityMapper.literal(plain(value));
	}

	public static String atMost(String field, Object value) {
		return AsdbEntityMapper.backtick(field) + " <= " + AsdbEntityMapper.literal(plain(value));
	}

	private void attempt(String statement) {
		try {
			client.execute(statement);
		} catch (AsdbClient.AsdbException e) {
			log.debug("asdb schema step skipped ({}): {}", statement, e.getMessage());
		}
	}

	// the entity as asdb stores it: enums by name, everything else as AsdbEntityMapper maps it
	private Map<String, Object> document(T entity) {
		Map<String, Object> doc = new LinkedHashMap<>();
		AsdbEntityMapper.toMap(entity).forEach((k, v) -> doc.put(k, plain(v)));
		return doc;
	}

	private static Object plain(Object value) {
		return switch (value) {
			// no braces no yield necessary
			case Enum<?> e -> e.name();

			case Map<?, ?> map -> {
				Map<String, Object> out = new LinkedHashMap<>();
				map.forEach((k, v) -> out.put(String.valueOf(k), plain(v)));
				yield out;
			}

			case Collection<?> items -> items.stream().map(AsdbDocumentStore::plain).toList();

			case null, default -> value;
		};
	}

	private T entity(Map<String, Object> doc) {
		T entity = OBJENESIS.newInstance(type);
		for (Field field : type.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
			write(field, entity, convert(doc.get(field.getName()), field.getType()));
		}
		return entity;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object convert(Object value, Class<?> target) {
		if (value == null) {
			if (target == int.class) return 0;
			if (target == long.class) return 0L;
			if (target == double.class) return 0d;
			if (target == boolean.class) return false;
			return null;
		}
		if (target == Instant.class) return Instant.ofEpochMilli(((Number) value).longValue());
		if (target == int.class || target == Integer.class) return ((Number) value).intValue();
		if (target == long.class || target == Long.class) return ((Number) value).longValue();
		if (target == double.class || target == Double.class) return ((Number) value).doubleValue();
		if (target.isEnum()) return Enum.valueOf((Class<Enum>) target, (String) value);
		return narrow(value);
	}

	// nested values have no declared type to convert to, so whole numbers become Integer where they fit
	private static Object narrow(Object value) {
		if (value instanceof Long n && n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) return n.intValue();
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			map.forEach((k, v) -> out.put(String.valueOf(k), narrow(v)));
			return out;
		}
		if (value instanceof List<?> items) return items.stream().map(AsdbDocumentStore::narrow).collect(Collectors.toList());
		return value;
	}

	private static Field idField(Class<?> type) {
		for (Field field : type.getDeclaredFields()) {
			if (field.isAnnotationPresent(Id.class)) {
				field.setAccessible(true);
				return field;
			}
		}
		throw new IllegalArgumentException(type.getName() + " has no @Id field");
	}

	private static Object read(Field field, Object target) {
		try {
			field.setAccessible(true);
			return field.get(target);
		} catch (IllegalAccessException e) {
			throw new AsdbClient.AsdbException("cannot read field " + field.getName(), e);
		}
	}

	private static void write(Field field, Object target, Object value) {
		try {
			field.setAccessible(true);
			field.set(target, value);
		} catch (IllegalAccessException e) {
			throw new AsdbClient.AsdbException("cannot set field " + field.getName(), e);
		}
	}

}
