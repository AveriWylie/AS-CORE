package dev.shayveri.core.observability;

import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;

/**
 * V6 - fire-and-forget audit writes on the virtual executor (an audit write must never slow or fail
 * a mutation; on failure it logs loudly instead). Call sites (the plan's law - EVERY DASH mutation):
 * ConfigService save+activate, JobService create, future key management.
 * Consumes: @Service, Executor (AsyncConfig bean), AuditStore (V4, rule-5 seam).
 * TODO(shahyar): audit(who, action, target, before, after) -> executor.execute(() -> store.record(...)).
 */
@Service
public class AuditService {
	public void audit(String who, String action, String target, Object before, Object after) {
		throw new UnsupportedOperationException("TODO(shahyar): V6");
	}
}
