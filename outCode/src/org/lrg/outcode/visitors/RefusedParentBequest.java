package org.lrg.outcode.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.lrg.outcode.IHindsight;
import org.lrg.outcode.builder.RelTypes;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class RefusedParentBequest {

	public void findRefusedParentBequest(Node classNode) {
		Node superClass = superClass(classNode);
		if (superClass != null) {
			List<Node> methods = getMemberList(classNode, "Method");
			List<Node> superMethods = getMemberList(superClass, "Method");
			classIgnoresBequest(classNode, superClass, methods, superMethods);
			classIsNotTooSmallAndSimple(classNode, methods);
		}
	}

	private Node superClass(Node classNode) {
		Iterable<Relationship> extendsRelations = classNode.getRelationships(RelTypes.EXTENDS, Direction.OUTGOING);
		for (Relationship relationship : extendsRelations) {
			Node endNode = relationship.getEndNode();
			return endNode;
		}
		return null;
	}

	private void classIgnoresBequest(Node classNode, Node superClass, List<Node> methods, List<Node> superMethods) {
		int computeNProtM = computeNProtM(superClass, superMethods);
		double computeBUR = computeBUR(classNode, superClass, methods);
		double computeBOvR = computeBOvR(classNode, methods);

		System.out.println(" class " + classNode.getProperty(IHindsight.ID) + " NProtM " + computeNProtM + " BUR " + computeBUR + " BOvR " + computeBOvR);
	}

	private List<Node> getMemberList(Node classNode, String label) {
		Iterable<Relationship> contains = classNode.getRelationships(RelTypes.CONTAINS, Direction.OUTGOING);
		Iterator<Relationship> iterator = contains.iterator();
		HashMap<Long, Node> members = new HashMap<Long, Node>();
		List<Node> methods = new ArrayList<Node>();

		while (iterator.hasNext()) {
			Relationship next = iterator.next();
			Node member = next.getEndNode();
			members.put(member.getId(), member);
		}
		for (Node node : members.values()) {
			if (node.hasLabel(Label.label(label))) { // if it's a
				methods.add(node);
			}
		}
		return methods;
	}

	private double computeBOvR(Node classNode, List<Node> methods) {
		double overriding = 0;
		for (Node node : methods) {
			if (node.hasProperty("overiding")) {
				overriding++;
			}
		}
		double bovr = overriding / methods.size();
		classNode.setProperty("BOvR", bovr);
		return bovr;
	}

	private double computeBUR(Node classNode, Node superClass, List<Node> methods) {
		double bur = 0;
		Set<Long> protectedFromSuper = new HashSet<Long>();

		getMemberList(superClass, "Method").iterator().forEachRemaining((node) -> {
			if (node.hasProperty("protected")) {
				protectedFromSuper.add(node.getId());
			}
		});
		getMemberList(superClass, "Field").iterator().forEachRemaining((node) -> {
			if (node.hasProperty("protected")) {
				protectedFromSuper.add(node.getId());
			}
		});

		if (protectedFromSuper.size() > 0) {
			Set<Long> calledMethods = new HashSet<Long>();
			Set<Long> accessedAttributes = new HashSet<Long>();
			for (Node method : methods) {
				Iterable<Relationship> efferentCalls = method.getRelationships(RelTypes.CALLS, Direction.OUTGOING);
				efferentCalls.forEach((r) -> {
					Node endNode = r.getEndNode();
					Node parentNode = parentNode(endNode);
					if (parentNode != null && haveSameID(parentNode, superClass)) {
						if (endNode.hasProperty("protected"))
							calledMethods.add(endNode.getId());
					}
				});
				Iterable<Relationship> efferentAccesses = method.getRelationships(RelTypes.ACCESSES, Direction.OUTGOING);
				efferentAccesses.forEach((r) -> {
					Node endNode = r.getEndNode();
					Node parentNode = parentNode(endNode);
					if (parentNode != null && haveSameID(parentNode, superClass)) {
						if (endNode.hasProperty("protected"))
							accessedAttributes.add(endNode.getId());
					}
				});
			}
			bur = (double) (calledMethods.size() + accessedAttributes.size()) / protectedFromSuper.size();
		}
		classNode.setProperty("BUR", bur);
		return bur;
	}

	private boolean haveSameID(Node parentNode, Node superClass) {
		return parentNode.getProperty(IHindsight.ID).equals(superClass.getProperty(IHindsight.ID));
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

	private int computeNProtM(Node classNode, List<Node> methods) {
		int noprtm = 0;
		for (Node node : methods) {
			if (node.hasProperty("protected"))
				noprtm++;
		}
		classNode.setProperty("NProtM", noprtm);
		return noprtm;
	}

	private void classIsNotTooSmallAndSimple(Node classNode, List<Node> methods) {
		computeNOM(classNode, methods);
		computeAMW(classNode, methods);
	}

	private void computeNOM(Node classNode, List<Node> methods) {
		classNode.setProperty("NOM", methods.size());

	}

	private int getWMC(Node classNode) {
		return (int) classNode.getProperty("WMC");
	}

	private void computeAMW(Node classNode, List<Node> methods) {
		classNode.setProperty("AMW", (double) getWMC(classNode) / methods.size());
	}

}
