package dev.shayveri.core.ingress.asdb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Talks to asdb over ABP/1, the binary protocol, on a pool of persistent
 * connections.
 *
 * <p>WHY THIS EXISTS RATHER THAN {@link AsdbClient}. Measured against the same
 * database, per single-document insert:
 *
 * <pre>
 *   HTTP, one connection per request     68.82 us     90% of it protocol
 *   ABP, connection reused               20.13 us     65% of it protocol
 *   ABP, batch of 100                     1.55 us     44x the HTTP path
 * </pre>
 *
 * Half of the HTTP number was opening and closing a TCP connection the previous
 * request had just thrown away. asdb's PROTOCOL.txt carries the full table.
 * {@code saveEvents} sends batches, so it lands on the 44x row.
 *
 * <p>WHY A POOL AND NOT ONE SHARED CONNECTION. asdb replies to requests in the
 * order they arrive on a connection, so two threads writing to one socket would
 * interleave their frames and read each other's replies. A pool gives each
 * caller a connection to itself for the duration of a request.
 *
 * <p>WHY {@link ReentrantLock} AND NOT {@code synchronized}. This application
 * runs on virtual threads ({@code spring.threads.virtual.enabled: true}) and
 * {@code TelemetryService} dispatches writes onto them. In Java 21 a virtual
 * thread that blocks inside {@code synchronized} PINS its carrier platform
 * thread, so a slow write would hold a real OS thread hostage and the pool
 * would throttle the whole executor. {@code ReentrantLock} parks the virtual
 * thread and releases the carrier. This is the kind of detail that costs
 * nothing to get right now and is very hard to diagnose later.
 *
 * <p>WHY A POOLED SOCKET IS RETRIED ONCE. A connection can be perfectly valid
 * when it goes into the pool and dead when it comes out, because asdb restarted
 * in between. The failure looks like a write succeeding into a closed socket and
 * a read returning EOF. Retrying once on a FRESH connection turns a restart into
 * a hiccup instead of a lost batch. It is deliberately once: a genuine outage
 * should surface, not be retried into a stall.
 */
public class AsdbBinaryClient implements AutoCloseable {

	private final String host;
	private final int port;
	private final int connectTimeoutMs;
	private final int requestTimeoutMs;
	private final ReentrantLock lock = new ReentrantLock();
	private final ArrayDeque<Conn> idle = new ArrayDeque<>();
	private final int maxIdle;
	private boolean closed;

	public AsdbBinaryClient(String host, int port, Duration connectTimeout, Duration requestTimeout, int maxIdle) {
		this.host = host;
		this.port = port;
		this.connectTimeoutMs = (int) connectTimeout.toMillis();
		this.requestTimeoutMs = (int) requestTimeout.toMillis();
		this.maxIdle = Math.max(1, maxIdle);
	}

	/** Inserts documents into a collection. Returns how many asdb reported. */
	public long insert(String collection, List<? extends Map<String, Object>> documents) {

		if (documents.isEmpty()) {
			return 0;
		}

		AbpCodec.Reply reply = roundTrip(AbpCodec.OP_INSERT, AbpCodec.insertPayload(collection, documents));

		if (reply.opcode() == AbpCodec.OP_ERROR) {
			throw new AsdbClient.AsdbException("asdb rejected an insert into " + collection + ": " + reply.error());
		}

		return reply.affected();
	}

	/** Runs one ASL statement. Used for DDL and for reads; inserts should use {@link #insert}. */
	public AbpCodec.Reply execute(String statement) {

		AbpCodec.Reply reply = roundTrip(AbpCodec.OP_EXEC, AbpCodec.execPayload(statement));

		if (reply.opcode() == AbpCodec.OP_ERROR) {
			throw new AsdbClient.AsdbException("asdb rejected: " + reply.error() + "  (statement: " + statement + ")");
		}

		return reply;
	}

	/** True when a connection can be made and the server answers a ping. Never throws. */
	public boolean isHealthy() {
		try {
			return roundTrip(AbpCodec.OP_PING, new byte[0]).opcode() == AbpCodec.OP_PONG;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/*
	One request, one reply, on a connection borrowed from the pool.

	A connection is returned to the pool only on success. Anything that throws
	discards it, because after a partial write or a failed read the stream
	position is unknown and reusing it would misalign every later frame on that
	socket. Sockets are cheap; a desynchronised protocol stream is not.
	*/
	private AbpCodec.Reply roundTrip(byte opcode, byte[] payload) {
		try {
			return attempt(opcode, payload, false);
		} catch (IOException first) {
			// A pooled socket that died while idle: retry once on a new one.
			// See the class note on why exactly once.
			try {
				return attempt(opcode, payload, true);
			} catch (IOException second) {
				throw new AsdbClient.AsdbException(
						"asdb unreachable at " + host + ":" + port + " (" + second.getMessage() + ")", second);
			}
		}
	}

	private AbpCodec.Reply attempt(byte opcode, byte[] payload, boolean forceFresh) throws IOException {

		Conn conn = forceFresh ? connect() : borrow();

		try {
			OutputStream out = conn.out;
			out.write(AbpCodec.frame(opcode, payload));
			out.flush();
			AbpCodec.Reply reply = AbpCodec.readFrame(conn.in);
			release(conn);
			return reply;
		} catch (IOException | RuntimeException e) {
			conn.closeQuietly();
			throw e;
		}
	}

	private Conn borrow() throws IOException {

		lock.lock();

		try {
			if (closed) {
				throw new AsdbClient.AsdbException("asdb client is closed");
			}
			Conn conn = idle.pollLast();
			if (conn != null) {
				return conn;
			}
		} finally {
			lock.unlock();
		}

		// connect OUTSIDE the lock: a connect can block for connectTimeoutMs
		// and holding the pool lock across it would stall every other caller.
		return connect();
	}

	private void release(Conn conn) {

		lock.lock();

		try {
			if (closed || idle.size() >= maxIdle) {
				conn.closeQuietly();
				return;
			}
			idle.addLast(conn);
		} finally {
			lock.unlock();
		}
	}

	private Conn connect() throws IOException {

		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
		/*
		TCP_NODELAY, for the same reason the server sets it. Nagle's algorithm
		holds a small write for up to 40ms waiting to coalesce with data that,
		in a request/response protocol, is never coming. On a 7us operation
		that is a 5700x latency penalty, and it appears intermittently, which
		makes it miserable to diagnose after the fact.
		*/
		socket.setTcpNoDelay(true);
		// A read timeout is what stops a hung server from parking this thread
		// forever. Without it, a server that accepts and then stops responding
		// leaks a connection per request until the pool is exhausted.
		socket.setSoTimeout(requestTimeoutMs);
		return new Conn(socket);
	}

	@Override
	public void close() {

		lock.lock();

		try {
			closed = true;
			for (Conn conn : idle) {
				conn.closeQuietly();
			}
			idle.clear();
		} finally {
			lock.unlock();
		}
	}

	/** A socket plus its buffered streams, kept together so they are discarded together. */
	private static final class Conn {

		private final Socket socket;
		private final DataInputStream in;
		private final OutputStream out;

		Conn(Socket socket) throws IOException {
			this.socket = socket;
			this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 8192));
			this.out = new BufferedOutputStream(socket.getOutputStream(), 8192);
		}

		void closeQuietly() {
			try {
				socket.close();
			} catch (IOException ignored) {
				// already broken; nothing useful to do or report
			}
		}
	}
}
