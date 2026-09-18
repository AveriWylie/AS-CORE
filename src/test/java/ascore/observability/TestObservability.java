package ascore.observability;

import ascore.jobs.QueueStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;

// the Module 7 collaborators for tests that build services by hand
public final class TestObservability {

	private TestObservability() { }

	public static AsCoreMetrics metrics(QueueStore queue) {return new AsCoreMetrics(new SimpleMeterRegistry(), queue, new DefaultSimpUserRegistry());}

	// writes inline, so a test can assert on the store as soon as the mutation returns
	public static AuditService audit(AuditStore store) {return new AuditService(store, Runnable::run);}

}
