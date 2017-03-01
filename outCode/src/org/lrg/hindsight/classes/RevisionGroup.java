package org.lrg.hindsight.classes;

import org.lrg.outcode.IHindsight;
import org.lrg.outcode.activator.GraphDB;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

import com.salexandru.xcore.utils.interfaces.Group;
import com.salexandru.xcore.utils.interfaces.IRelationBuilder;
import com.salexandru.xcore.utils.metaAnnotation.RelationBuilder;

import outcode.metamodel.entity.XClass;
import outcode.metamodel.entity.XClassVersion;
import outcode.metamodel.factory.Factory;

@RelationBuilder
public class RevisionGroup implements IRelationBuilder<XClassVersion, XClass> {

	@Override
	public Group<XClassVersion> buildGroup(XClass entity) {
		Group<XClassVersion> res = new Group<>();
		GraphDatabaseService dbService = GraphDB.instance.getDB();
		Transaction tx = dbService.beginTx();
		ResourceIterator<Node> allArticles = dbService.findNodes(Label.label("Type"));
		while (allArticles.hasNext()) {
			Node node = allArticles.next();
			if (node.getProperty(IHindsight.NAME).equals(entity.getUnderlyingObject().getElementName())) {
				res.add(Factory.getInstance().createXClassVersion(node));
			}
		}
		allArticles.close();
		tx.success();
		return res;
	}

}
