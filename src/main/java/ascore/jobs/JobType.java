package ascore.jobs;

/**
 * J1a - the kinds of lab work. Enum because job type is our vocabulary: a typo'd type must fail
 * loudly at the HTTP edge (Jackson rejects an unknown enum value -> 400 via C2), never silently
 * enqueue an un-runnable job. Capability filtering at claim time keys off these values.
 */
public enum JobType {
	TEXTURE_BAKE, MESH_OPTIMIZE, LIGHT_BAKE, PATHFIND_PRECOMPUTE, CUSTOM
}
