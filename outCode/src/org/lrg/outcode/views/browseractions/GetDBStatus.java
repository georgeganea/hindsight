package org.lrg.outcode.views.browseractions;

import org.lrg.outcode.activator.GraphDB;
import org.neo4j.graphdb.Result;

public class GetDBStatus implements IBrowserAction {

	@Override
	public String onMessage(String messageName, Object[] args) {
		// File gitDir = new RepoDirFinder().findRepoDir();
		Result execute = GraphDB.instance.getDB().execute("MATCH () RETURN COUNT(*) AS node_count;");
		return execute.resultAsString();
	}

}
