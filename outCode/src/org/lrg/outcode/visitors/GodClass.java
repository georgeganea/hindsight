package org.lrg.outcode.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.lrg.outcode.IHindsight;
import org.lrg.outcode.builder.RelTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class GodClass {

	private class Pair {
		private Node right;
		private Node left;

		public Pair(Node left, Node right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public String toString() {
			// return "[" + left.getProperty(IHindsight.NAME) + " " + left.getId() + " ,
			// " + right.getProperty(IHindsight.NAME) + " "+right.getId() + "]";
			if (left.getId() < right.getId())
				return left.getId() + " " + right.getId();
			else
				return right.getId() + " " + left.getId();
		}
	}

	private HashMap<String, Pair> uniquePairs = new HashMap<String, Pair>();

	public void findGodClasses(Node clazz) {
		System.out.println("" + clazz.getProperty(IHindsight.NAME));
		Iterable<Relationship> contains = clazz.getRelationships(RelTypes.CONTAINS, Direction.OUTGOING);
		Iterator<Relationship> iterator = contains.iterator();
		HashMap<Long, Node> members = new HashMap<Long, Node>();

		while (iterator.hasNext()) {
			Relationship next = iterator.next();
			Node member = next.getEndNode();
			members.put(member.getId(), member);
		}
		System.out.println("\t members: " + members.size());
		uniquePairs.clear();
		double n = 0;
		int atfd = 0;
		int wmc = 0;
		for (Node node : members.values()) {
			if (node.hasLabel(Label.label("Field"))) { // if it's a field
				// we compute
				// TCC
				List<Long> afferentAccessorMethods = new ArrayList<Long>();
				Iterable<Relationship> relationships = node.getRelationships(RelTypes.ACCESSES, Direction.INCOMING);
				for (Relationship relationship : relationships) {
					Node startNode = relationship.getStartNode();
					if (members.get(startNode.getId()) != null) {
						if (!afferentAccessorMethods.contains(startNode.getId()))
							afferentAccessorMethods.add(startNode.getId());
					}
				}

				for (int i = 0; i < afferentAccessorMethods.size() - 1; i++) {
					for (int j = i + 1; j < afferentAccessorMethods.size(); j++) {
						Pair pair = new Pair(members.get(afferentAccessorMethods.get(i)), members.get(afferentAccessorMethods.get(j)));
						if (uniquePairs.get(pair.toString()) == null)
							uniquePairs.put(pair.toString(), pair);
					}
				}
			}
			if (node.hasLabel(Label.label("Method"))) {
				n += 1;
				if (node.hasProperty("foreignAccesses")) {
					Object foreignAccesses = node.getProperty("foreignAccesses");
					atfd += (Integer) foreignAccesses;
				} else
					System.out.println("node withID " + node.getProperty(IHindsight.ID) + " does not have foreingAccess set");
				if (node.hasProperty("cyclomaticComplexity")) {
					Object cyclomaticComplexity = node.getProperty("cyclomaticComplexity");
					wmc += (Integer) cyclomaticComplexity;
				} else
					System.out.println("node withID " + node.getProperty(IHindsight.ID) + " does not have cyclomaticComplexity set");
			}
		}
		double tcc = 1;
		if (n > 1) {
			tcc = (uniquePairs.size()) / ((n * (n - 1)) / 2);
			if (tcc > 1) {
				System.out.println("\t n=" + n + " (n * (n - 1)) / 2 = " + ((n * (n - 1)) / 2));
				// for (Node node : members.values()) {
				// System.out.println("\t member " + node.getProperty(IHindsight.NAME));
				// }
				System.out.println("\t uniquePairs= " + uniquePairs.size());
				for (Pair pair : uniquePairs.values()) {
					System.out.println("\t pair " + pair);
				}
			}
		}

		System.out.println("TCC = " + tcc);
		clazz.setProperty("TCC", tcc);
		System.out.println("ATFD = " + atfd);
		System.out.println("WMC = " + wmc);
		clazz.setProperty("ATFD", atfd);
		clazz.setProperty("WMC", wmc);
	}
}
