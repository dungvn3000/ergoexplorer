package vn.erg.explorer.index;

import tools.jackson.databind.JsonNode;

/** A full block as fetched from the node, kept until it is written. */
public record IndexedBlock(long height, String id, JsonNode json) {
}
