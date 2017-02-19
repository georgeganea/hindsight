package org.lrg.outcode.eclipse.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lrg.outcode.IHindsight;
import org.lrg.outcode.activator.GraphDB;
import org.lrg.outcode.builder.RelTypes;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.lrg.outcode.views.Hindsight;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

public class EntityAccessesEvolution {

	class Indexer {
		Integer index = 0;
		String commit = "";
	}

	class RelationHistory {
		private String entityId;
		private String fromFile;
		private HashMap<Integer, String> rels;
		private Indexer i;

		public RelationHistory(String attrId, String fromFile, Indexer i) {
			this.entityId = attrId;
			this.fromFile = fromFile;
			this.rels = new HashMap<>();
			this.i = i;
		}

		public void add(int index, String id) {
			rels.put(index, id);
		}

		public JSONObject toJson() {
			JSONObject result = new JSONObject();
			result.put("entityId", entityId);
			result.put("filePath", fromFile);
			JSONArray rels = new JSONArray();
			for (int i = 0; i < this.i.index; i++) {
				rels.put(i, this.rels.get(i));
			}
			result.put("rels", rels);
			return result;
		}

		@Override
		public String toString() {
			return toJson().toString(2);
		}
	}

	private GraphDatasource db = GraphDatasource.INSTANCE;

	public Object execute(ICompilationUnit unit) throws ExecutionException {

		IJavaElement selected;
		Transaction beginTx = null;
		try {
			if (unit.getTypes().length > 0) {
				selected = unit.getTypes()[0];
			} else {
				return null;
			}

			System.out.println("selected = " + selected);

			if (selected.getElementType() == IJavaElement.TYPE) {
				beginTx = GraphDB.instance.getDB().beginTx();
				Node findFirstNodeForElementID = db.findFirsNodeForElementID(selected);
				JSONArray iJavaElementNamesArray = new JSONArray();
				JSONObject showAccessesHistoryForClass = new JSONObject();
				showAccessesHistoryForClass.put("msg", "showAccessesHistoryForClass");

				if (findFirstNodeForElementID != null) {
					Node next = findFirstNodeForElementID;
					// the list of all accesses ever, it's a treemap because we care about the order

					Map<String, RelationHistory> data = new TreeMap<>();
					Map<String, Integer> accessToArrayIndex = new TreeMap<String, Integer>();

					final Indexer i = new Indexer();
					final String portableString = unit.getResource().getProjectRelativePath().toPortableString();
					String commitID = null;
					String prev_commit = null;
					do {

						JSONObject jsonObject = new JSONObject();
						if (commitID != null)
							prev_commit = commitID.toString();
						// jsonObject.put("prev_" + IHindsight.COMMITID, commitID);

						commitID = next.getProperty(IHindsight.COMMITID).toString();

						i.commit = prev_commit + "-" + commitID;

						jsonObject.put(IHindsight.COMMITID, commitID);
						final Node c = next;

						// go through all methods, get all accesses and print them

						List<Integer> currentNodeAccesses = new ArrayList<Integer>();// contains a list of indexes from the map of all accesses ever
						Iterable<Relationship> containedMethods = next.getRelationships(RelTypes.CONTAINS, Direction.OUTGOING);
						Stream<Relationship> stream = StreamSupport.stream(containedMethods.spliterator(), false);

						stream.map(contains -> contains.getEndNode()).filter(method -> method.hasLabel(Label.label("Method"))).forEach(method -> {
							Stream<Relationship> accessedFields = StreamSupport.stream(method.getRelationships(RelTypes.ACCESSES, Direction.OUTGOING).spliterator(), false);
							accessedFields.map(access -> access.getEndNode())
									.filter(attr -> !attr.getRelationships(RelTypes.PARENT, Direction.OUTGOING).iterator().hasNext() || !(attr.getRelationships(RelTypes.PARENT, Direction.OUTGOING).iterator().next().getEndNode().equals(c))).forEach((attr) -> {
										Object attrId = attr.getProperty(IHindsight.ID);

										if (data.containsKey(attrId)) {
											data.get(attrId).add(i.index, i.commit);
										} else {
											data.put(attrId.toString(), new RelationHistory(attrId.toString(), portableString, i));
											data.get(attrId).add(i.index, i.commit);
										}

										if (accessToArrayIndex.containsKey(attrId)) {
											if (!currentNodeAccesses.contains(accessToArrayIndex.get(attrId)))
												currentNodeAccesses.add(accessToArrayIndex.get(attrId));
										} else {
											int size = accessToArrayIndex.size();
											if (!currentNodeAccesses.contains(size))
												currentNodeAccesses.add(size);
											accessToArrayIndex.put(attrId.toString(), size);
										}
									});
						});
						JSONArray accessesArray = new JSONArray();
						currentNodeAccesses.forEach(access -> {
							accessesArray.put(access);
						});

						next = findNextNode(next);
						i.index++;
					} while (next != null);

					accessToArrayIndex.entrySet().stream().sorted((o1, o2) -> o1.getValue().compareTo(o2.getValue())).forEach(entry -> {
						JSONObject accessedAttribute = new JSONObject();
						accessedAttribute.put(entry.getValue().toString(), entry.getKey());
						iJavaElementNamesArray.put(accessedAttribute);
					});

					JSONArray dataArray = new JSONArray();

					data.values().stream().sorted((v1, v2) -> {
						int result = v1.rels.keySet().stream().mapToInt(k -> k).min().getAsInt() - v2.rels.keySet().stream().mapToInt(k -> k).min().getAsInt();
						if (result == 0)
							result = v2.rels.keySet().stream().mapToInt(k -> 1).sum() - v1.rels.keySet().stream().mapToInt(k -> 1).sum();
						return result;
					}).forEach(v -> dataArray.put(v.toJson()));

					showAccessesHistoryForClass.put("history", dataArray);

					System.out.println("data " + dataArray);
				}
				if (PlatformUI.getWorkbench().getWorkbenchWindowCount() > 0) {
					Display.getDefault().asyncExec(new Runnable() {

						@Override
						public void run() {
							IViewPart showView;
							try {
								showView = PlatformUI.getWorkbench().getWorkbenchWindows()[0].getActivePage().showView(Hindsight.VIEW_ID);
								if (showView instanceof Hindsight) {
									Hindsight hindsight = (Hindsight) showView;
									String replaceAll = showAccessesHistoryForClass.toString(4).replaceAll("\"", "'");
									System.out.println("send the message \n" + replaceAll);
									hindsight.sendMessage(replaceAll);
								}
							} catch (PartInitException e) {
								e.printStackTrace();
							}
						}
					});

				}
				beginTx.success();
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		} finally {
			if (beginTx != null) {
				beginTx.close();
			}
		}
		return null;
	}

	private Node findNextNode(Node node) {
		Iterable<Relationship> toNext = node.getRelationships(RelTypes.NEXT, Direction.OUTGOING);
		if (toNext.iterator().hasNext())
			return toNext.iterator().next().getEndNode();
		return null;
	}

}
