package org.lrg.outcode.builder.db;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.lrg.outcode.IHindsight;
import org.lrg.outcode.activator.GraphDB;
import org.lrg.outcode.builder.ModelVistor.OPERATIONS;
import org.lrg.outcode.builder.RelTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;

public class GraphDatasource {

	public static GraphDatasource INSTANCE = new GraphDatasource();
	public Map<String, Index<Node>> elementsIndex = new HashMap<String, Index<Node>>();

	private GraphDatasource() {

	};

	public Node addNodeToDB(IJavaElement parent, IJavaElement element, String version, String commitID, int added, int removed) {
		Node parentNode = findLatestNodeForElementID(parent);
		if (parentNode == null)
			parentNode = createIJavaElementNode(parent, version, commitID, added, removed);
		Node n = createIJavaElementNode(element, version, commitID, added, removed);
		parentNode.createRelationshipTo(n, RelTypes.CONTAINS);
		n.createRelationshipTo(parentNode, RelTypes.PARENT);
		return n;
	}

	public Node createIJavaElementNode(IJavaElement element, String version, String commitID, int added, int removed) {
		String handleIdentifier = element.getHandleIdentifier();
		String elementName = element.getElementName();
		String label = computeLabel(element);
		Node n = createEntityNode(handleIdentifier, elementName, label, element, version, commitID, added, removed);
		return n;
	}

	private String computeLabel(IJavaElement element) {
		String label = element.getClass().getSimpleName();
		label = label.replace("Resolved", "");
		label = label.replace("Source", "");
		return label;
	}

	public Node createEntityNode(String handleIdentifier, String elementName, String stringLabel, IJavaElement element, String version, String commitID, int added, int removed) {
		GraphDatabaseService graphDb = GraphDB.instance.getDB();

		String vidToFind = element.getHandleIdentifier() + "_" + version;
		Label label = Label.label(stringLabel);
		Node n = graphDb.findNode(label, IHindsight.VID, vidToFind);
		if (n == null) {
			Node createNode = graphDb.createNode();
			createNode.addLabel(label);
			createNode.setProperty(IHindsight.ID, handleIdentifier);
			createNode.setProperty(IHindsight.VID, vidToFind);

			if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT)

				createNode.setProperty(IHindsight.NAME, element.getPath().makeRelativeTo(element.getJavaProject().getPath()).toPortableString().replaceAll("/", "."));
			else
				createNode.setProperty(IHindsight.NAME, elementName);
			if (element instanceof IMethod) {
				IMethod method = (IMethod) element;
				try {
					createNode.setProperty("code", method.getSource() != null ? method.getSource() : "");
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}
			n = createNode;
			n.setProperty(IHindsight.COMMIT, version);
			n.setProperty(IHindsight.COMMITID, commitID);
			n.setProperty("addedLOC", added);
			n.setProperty("removedLOC", removed);
		}
		return findLatestVersion(n);
	}

	public Node findFirsNodeForElementID(IJavaElement member) {
		Node result = null;
		GraphDatabaseService db = GraphDB.instance.getDB();
		Label label = Label.label(computeLabel(member));
		ResourceIterator<Node> nodes = db.findNodes(label, IHindsight.ID, member.getHandleIdentifier());
		if (nodes.hasNext())
			result = nodes.next();
		nodes.close();
		if (result != null)
			result = findFirstVersion(result);
		return result;
	}

	public Node findFirstVersion(Node n) {
		Node result = n;
		Iterable<Relationship> toBefore = n.getRelationships(RelTypes.NEXT, Direction.INCOMING);

		while (toBefore != null && toBefore.iterator().hasNext()) {
			Relationship next = toBefore.iterator().next();
			Node end = next.getEndNode();
			Node start = next.getStartNode();
			if (end.equals(start)) {// safety check, this should not happen
				System.out.println("found NEXT relation to the same node " + start);
				break;
			}
			result = end;
			toBefore = result.getRelationships(RelTypes.NEXT, Direction.INCOMING);
		}

		return result;
	}

	public Node findLatestNodeForElementID(IJavaElement member) {
		Node result = null;
		GraphDatabaseService db = GraphDB.instance.getDB();
		Label label = Label.label(computeLabel(member));
		ResourceIterator<Node> nodes = db.findNodes(label, IHindsight.ID, member.getHandleIdentifier());
		if (nodes.hasNext())
			result = nodes.next();
		nodes.close();
		if (result != null)
			result = findLatestVersion(result);
		return result;
	}

	public Node findLatestVersion(Node n) {
		Node result = n;
		Iterable<Relationship> toNext = n.getRelationships(RelTypes.NEXT, Direction.OUTGOING);
		while (toNext != null && toNext.iterator().hasNext()) {
			Relationship next = toNext.iterator().next();

			Node end = next.getEndNode();
			Node start = next.getStartNode();
			if (end.equals(start))
				break;
			result = end;
			toNext = result.getRelationships(RelTypes.NEXT, Direction.OUTGOING);
		}
		return result;
	}

	public Index<Node> getElementTypeIndex(IJavaElement element, String version) {
		String key = element.getElementType() + version;

		if (elementsIndex.containsKey(key))
			return elementsIndex.get(key);

		Transaction tx = GraphDB.instance.getDB().beginTx();
		try {
			elementsIndex.put(key, GraphDB.instance.getDB().index().forNodes(key));
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tx.close();
		}
		return elementsIndex.get(key);
	}

	public Node addProjectToDb(IJavaProject javaProject, String commit, String commitID) {
		Node n = createIJavaElementNode(javaProject, commit, commitID, 0, 0);
		n.setProperty(IHindsight.COMMIT, Long.parseLong(commit));
		n.setProperty(IHindsight.COMMITID, commitID);
		return n;
	}

	public Node addModifiedVersion(ICompilationUnit iCompilationUnit, OPERATIONS modify, String version) {
		String handleIdentifier = iCompilationUnit.getHandleIdentifier();
		GraphDatabaseService db = GraphDB.instance.getDB();
		Node n = null;
		Label label = Label.label(computeLabel(iCompilationUnit));
		String computeVersionWhenIntroduced = computeVersionWhenIntroduced(iCompilationUnit);
		if (computeVersionWhenIntroduced.length() > 0) {
			String vidToFind = handleIdentifier + "_" + computeVersionWhenIntroduced;
			try {
				n = db.findNode(label, IHindsight.VID, vidToFind);
				if (n != null) {
					n = findLatestVersion(n);// this should not be necessary if
												// @computeVersionWhenIntroduced
												// actually returned the latest
												// version
					Node createNode = db.createNode();
					createNode.addLabel(Label.label(computeLabel(iCompilationUnit)));
					createNode.setProperty(IHindsight.NAME, iCompilationUnit.getElementName());
					createNode.setProperty(IHindsight.ID, handleIdentifier);
					createNode.setProperty(IHindsight.VID, version);
					n.createRelationshipTo(createNode, RelTypes.NEXT);
					return createNode;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/* TODO refactor so that it returns latest version */
	private String computeVersionWhenIntroduced(IJavaElement member) {
		String result = "";
		GraphDatabaseService db = GraphDB.instance.getDB();
		Label label = Label.label(computeLabel(member));
		ResourceIterator<Node> nodes = db.findNodes(label, IHindsight.ID, member.getHandleIdentifier());
		if (nodes.hasNext())
			result = nodes.next().getProperty(IHindsight.COMMIT).toString();
		nodes.close();
		return result;
	}

	public Node addNextVersionNodeToDB(IJavaElement parent, IJavaElement element, String version, String commitID, int added, int removed) {
		String handleIdentifier = element.getHandleIdentifier();
		GraphDatabaseService graphDb = GraphDB.instance.getDB();
		Label label = Label.label(computeLabel(element));
		String computeVersionWhenIntroduced = computeVersionWhenIntroduced(element);
		Node n = null;
		if (computeVersionWhenIntroduced.length() > 0) {
			String vidToFind = handleIdentifier + "_" + computeVersionWhenIntroduced;
			try {
				n = graphDb.findNode(label, IHindsight.VID, vidToFind);
				if (n != null) {
					Node createIJavaElementNode = createIJavaElementNode(element, version, commitID, added, removed);
					n = findLatestVersion(n);
					if (!n.getProperty(IHindsight.VID).equals(createIJavaElementNode.getProperty(IHindsight.VID)))
						n.createRelationshipTo(createIJavaElementNode, RelTypes.NEXT);
					n = createIJavaElementNode;
				} else
					n = addNodeToDB(parent, element, version, commitID, added, removed);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else
			n = addNodeToDB(parent, element, version, commitID, added, removed);
		return n;
	}

	public void createCommitNode(String commitID, int commitSize) {

	}

}
