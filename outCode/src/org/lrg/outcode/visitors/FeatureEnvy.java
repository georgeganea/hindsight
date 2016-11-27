package org.lrg.outcode.visitors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lrg.outcode.IHindsight;
import org.lrg.outcode.builder.RelTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class FeatureEnvy {

	private ArrayList<Node> featureEnvys = new ArrayList<Node>();
	public static ArrayList<String> uniqueFiles = new ArrayList<String>();
	private boolean addInUniqueFiles;

	private final class ConsumerImplementation implements Consumer<Relationship> {
		private final Counter c;
		private ArrayList<Node> currentClassAndAncestors;

		private ConsumerImplementation(ArrayList<Node> currentClassAndAncestors, Counter c) {
			this.currentClassAndAncestors = currentClassAndAncestors;
			this.c = c;
		}

		@Override
		public void accept(Relationship t) {
			Node endNode = t.getEndNode();
			if (t.isType(RelTypes.CALLS) && !endNode.hasProperty("get"))
				return;
			if (t.getEndNode() != null) {
				Node accessedClass = parentNode(endNode);
				if (currentClassAndAncestors.contains(accessedClass))
					c.currentClassAccesses++;
				else if (c.accessedClasses.size() > 0 && !c.accessedClasses.contains(accessedClass))
					c.currentClassAccesses++;// fake access so we filter
												// this method, we only
												// care for accesses
				else if (c.accessedClasses.isEmpty()) {
					c.foreignAccesses++;
					c.accessedClasses.add(accessedClass);
				} else
					c.foreignAccesses++;
			}
		}
	}

	private class Counter {
		Integer currentClassAccesses = 0;
		List<Node> accessedClasses = new ArrayList<Node>();
		protected Integer foreignAccesses = 0;
	}

	public FeatureEnvy(boolean addInUniqueFiles) {
		this.addInUniqueFiles = addInUniqueFiles;
	}

	public void findFeatureEnvy(Node method) {
		final Node currentClass = parentNode(method);
		if (currentClass != null) {
			ArrayList<Node> currentClassAndAncestors = computeCurrentClassAndAncestors(currentClass);
			Iterable<Relationship> accesses = method.getRelationships(RelTypes.ACCESSES, Direction.OUTGOING);
			final Counter c = new Counter();
			accesses.forEach(new ConsumerImplementation(currentClassAndAncestors, c));
			Iterable<Relationship> calls = method.getRelationships(RelTypes.CALLS, Direction.OUTGOING);
			calls.forEach(new ConsumerImplementation(currentClassAndAncestors, c));

			method.setProperty("currentClassAccesses", c.currentClassAccesses);
			method.setProperty("foreignAccesses", c.foreignAccesses);
			if (c.currentClassAccesses == 0 && c.foreignAccesses > 2) {
				featureEnvys.add(method);
				printFullName(method);
			}
		}
	}

	private ArrayList<Node> computeCurrentClassAndAncestors(Node startClas) {
		ArrayList<Node> result = new ArrayList<Node>();
		result.add(startClas);
		Node currentClass = startClas;
		while (currentClass != null && currentClass.getRelationships(RelTypes.EXTENDS, Direction.OUTGOING).iterator().hasNext()) {
			Node endNode = currentClass.getRelationships(RelTypes.EXTENDS, Direction.OUTGOING).iterator().next().getEndNode();
			if (endNode != null)
				result.add(endNode);
			currentClass = endNode;
		}
		return result;
	}

	private void printFullName(Node method) {
		method.setProperty("fe", "true");
		Node parentClass = parentNode(method);
		if (parentClass != null) {
			Node parentPackage = parentNode(parentNode(parentClass));
			if (parentPackage != null) {

				String fileName = parentPackage.getProperty(IHindsight.NAME) + "." + parentClass.getProperty(IHindsight.NAME);
				if (!uniqueFiles.contains(fileName) && addInUniqueFiles)
					uniqueFiles.add(fileName);
				System.out.println(parentPackage.getProperty(IHindsight.NAME) + "." + parentClass.getProperty(IHindsight.NAME));
				// System.out.println(method.getProperty("code"));
			}
		}

	}

	private Node parentNode(Node node) {
		if (node != null) {
			Iterable<Relationship> toParent = node.getRelationships(RelTypes.PARENT, Direction.OUTGOING);
			if (toParent.iterator().hasNext()) {
				Node parent = toParent.iterator().next().getEndNode();
				return parent;
			}
		}
		return null;
	}

	public void listFiles() {
		System.out.println("total " + uniqueFiles.size());
	}

}
