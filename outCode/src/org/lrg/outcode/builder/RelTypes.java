package org.lrg.outcode.builder;

import org.neo4j.graphdb.RelationshipType;

public enum RelTypes implements RelationshipType {
	NEXT, CONTAINS, HAS_PARAM, IS_OF_TYPE, ACCESSES, PARENT, EXTENDS, CALLS
}
