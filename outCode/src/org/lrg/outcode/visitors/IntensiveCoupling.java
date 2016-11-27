package org.lrg.outcode.visitors;

import java.util.HashSet;
import java.util.Set;

import org.lrg.outcode.builder.RelTypes;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class IntensiveCoupling {

	public void findIntensiveCoupling(Node method) {
		Iterable<Relationship> efferentCalls = method.getRelationships(RelTypes.CALLS, Direction.OUTGOING);
		Set<Long> calledMethods = new HashSet<Long>();
		Set<Long> calledClasses = new HashSet<Long>();
		efferentCalls.forEach((r) -> {
			Node endNode = r.getEndNode();
			Node parentNode = parentNode(endNode);
			if (parentNode != null) {
				calledMethods.add(endNode.getId());
				calledClasses.add(parentNode.getId());
			}
		} );
		
//		Iterable<Relationship> afferentCalls = method.getRelationships(RelTypes.CALLS, Direction.INCOMING);
//		Set<Long> callingMethods = new HashSet<Long>();
//		Set<Long> callingClasses = new HashSet<Long>();
//		afferentCalls.forEach((r) -> {
//			Node startNode = r.getStartNode();
//			Node parentNode = parentNode(startNode);
//			if (parentNode != null) {
//				callingMethods.add(startNode.getId());
//				callingClasses.add(parentNode.getId());
//			}
//		} );
		
		method.setProperty("calledMethods", calledMethods.size());
		method.setProperty("calledClasses", calledClasses.size());
//		method.setProperty("callingMethods", callingMethods.size());
//		method.setProperty("callingClasses", callingClasses.size());
	}

	private Node parentNode(Node node) {
		Node latestNodeForElementID = GraphDatasource.INSTANCE.findLatestVersion(node);
		Iterable<Relationship> toParent = latestNodeForElementID.getRelationships(RelTypes.PARENT, Direction.OUTGOING);
		if (toParent.iterator().hasNext()) {
			Node parent = toParent.iterator().next().getEndNode();
			return parent;
		}
		return null;
	}
}
