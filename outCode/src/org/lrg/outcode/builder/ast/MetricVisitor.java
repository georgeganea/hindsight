package org.lrg.outcode.builder.ast;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.WhileStatement;

public class MetricVisitor extends ASTVisitor{
	private int cyclo = 1;
	private int statements = 0;
		
	public MetricVisitor() {
		super();
	}
	
	public boolean visit(IfStatement loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(SwitchCase loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(ForStatement loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(DoStatement loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(WhileStatement loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(EnhancedForStatement loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(ConditionalExpression loc){
		cyclo++;
		return true;	 
	}
	
	public boolean visit(Block loc){
		statements += loc.statements().size();
		return true;	 
	}
	
	public boolean visit(InfixExpression loc){
		if (loc.getOperator() == InfixExpression.Operator.CONDITIONAL_AND || loc.getOperator() == InfixExpression.Operator.CONDITIONAL_OR)
			cyclo++;
		return true;	 
	}
	
	public int getCyclomaticComplexity(){
		return cyclo;
	}
	
	public int getNumberOfStatements(){
		return statements;
	}
	
	public void process(ASTNode node){
		node.accept(this);
	}

}