package org.lrg.hindsight.classes.version;

import java.util.Iterator;

import org.lrg.outcode.builder.RelTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import com.salexandru.xcore.utils.interfaces.IPropertyComputer;
import com.salexandru.xcore.utils.metaAnnotation.PropertyComputer;

import outcode.metamodel.entity.XClassVersion;

@PropertyComputer
public class ClassVersionATFD implements IPropertyComputer<Integer, XClassVersion> {

	@Override
	public Integer compute(XClassVersion entity) {
		int atfd = 0;
		Node node = entity.getUnderlyingObject();
		Iterable<Relationship> contains = node.getRelationships(RelTypes.CONTAINS, Direction.OUTGOING);
		Iterator<Relationship> iterator = contains.iterator();
		while (iterator.hasNext()) {
			Relationship next = iterator.next();
			Node member = next.getEndNode();
			if (member.hasLabel(Label.label("Method"))) {
				System.out.println(member);
				if (member.hasProperty("foreignAccesses")) {
					Object foreignAccesses = member.getProperty("foreignAccesses");
					atfd += (Integer) foreignAccesses;
				}
			}
		}
		return atfd;
	}

}
