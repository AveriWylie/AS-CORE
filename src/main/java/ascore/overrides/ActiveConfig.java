package ascore.overrides;

// the assembled config as served, and the etag of exactly those bytes
public record ActiveConfig(String body, String etag) { }
