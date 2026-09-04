package ascore.ingress;

import java.util.List;

/**
 * A5 - the storage seam: the ONLY doorway to persistence for this module
 * (development rule 5; the future asdb swap point). Nothing else in the
 * module may import a Mongo API.
 *
 * Consumes: nothing - pure Java interface, entirely ours. That is the point:
 * the seam itself depends on nobody.
 *
 * Given complete (interfaces are contracts, and this one's exactness is
 * what the whole rule-5 design hangs on). Implementing it is A6.
 */

public interface TelemetryStore {

	void saveSnapshot(TelemetrySnapshot snapshot);

	void saveEvents(List<GameEvent> events);

}
