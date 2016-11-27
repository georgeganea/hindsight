package org.lrg.outcode.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.lrg.outcode.activator.GraphDB;
import org.neo4j.graphdb.Result;

public class CleanDB extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Result execute = GraphDB.instance.getDB().execute("MATCH (n) DETACH DELETE n;");
		return execute.resultAsString();
	}

}
