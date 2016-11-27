package org.lrg.outcode.activator;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.lrg.outcode.IHindsight;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.schema.Schema;

public class GraphDB {
	private static String DB_PATH = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toPortableString() + "/.hindsight/graph.db";
	private static final String[] labels = { "CompilationUnit", "PackageFragment", "Type", "Method", "Field" };
	private GraphDatabaseService graphDb;

	private GraphDB() {

	}

	private void createIndexForLabel(String label, Schema schema) {

		schema.indexFor(Label.label(label)).on(IHindsight.ID).create();
		schema.indexFor(Label.label(label)).on(IHindsight.VID).create();
		schema.indexFor(Label.label(label)).on(IHindsight.COMMIT).create();

	}

	public GraphDatabaseService getDB() {
		return graphDb;
	}

	public static GraphDB instance = new GraphDB();

	public void startDB() {
		if (graphDb == null) {
			graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File(DB_PATH));

			try (Transaction tx = graphDb.beginTx()) {
				Schema schema = graphDb.schema();
				if (!schema.getIndexes().iterator().hasNext()) {
					for (String string : labels) {
						createIndexForLabel(string, schema);
					}
				}
				tx.success();
			}
			registerShutdownHook(graphDb);
		}
	}

	public void stopDB() {
		graphDb.shutdown();
	}

	private static void registerShutdownHook(final GraphDatabaseService graphDb) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				graphDb.shutdown();
			}
		});
	}
}
