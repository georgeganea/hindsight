package org.lrg.outcode.builder.ast;

import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.lrg.outcode.builder.RelTypes;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.neo4j.graphdb.Node;

public class OutCodeVisitor extends ASTVisitor {

	private GraphDatasource db = GraphDatasource.INSTANCE;
	private String commit;
	private ITypeBinding currentTypeResolvedBinding;
	private String commitID;
	private int added;
	private int removed;

	public OutCodeVisitor(String commit, String commitID, int added, int removed) {
		this.commit = commit;
		this.commitID = commitID;
		this.removed = removed;
		this.added = added;
	}

	/*
	 * public boolean visit(SingleVariableDeclaration singleVariableDeclaration) { if (singleVariableDeclaration.getParent() instanceof MethodDeclaration) {
	 * addParameterToMethod(singleVariableDeclaration); } return true; }
	 * 
	 * private void addParameterToMethod(SingleVariableDeclaration singleVariableDeclaration) { MethodDeclaration methodDeclaration = (MethodDeclaration)
	 * singleVariableDeclaration.getParent(); IJavaElement methodJavaElement = getIJavaElement(methodDeclaration); if (methodJavaElement != null) { Node methodIJavaElementNode =
	 * db.createIJavaElementNode(methodJavaElement, dbService, commit); SimpleName simpleName = singleVariableDeclaration.getName(); String identifier = simpleName.getIdentifier();
	 * // hacky create node call, createNode should be private Node createParameterNode = db.createNode(dbService, methodJavaElement.getHandleIdentifier() + "_" + identifier,
	 * identifier, "Parameter", methodJavaElement, commit); methodIJavaElementNode.createRelationshipTo(createParameterNode, RelTypes.HAS_PARAM);
	 * 
	 * if (singleVariableDeclaration.getType().isPrimitiveType()) { createParameterNode.setProperty("primitive type", singleVariableDeclaration.getType().toString()); } else {
	 * ITypeBinding typeBinding = singleVariableDeclaration.getType().resolveBinding(); IJavaElement parameterTypeIJavaElement = typeBinding.getJavaElement(); if
	 * (parameterTypeIJavaElement != null) { Node parameterIJavaElementNode = db.createIJavaElementNode(parameterTypeIJavaElement, dbService, commit);
	 * createParameterNode.createRelationshipTo(parameterIJavaElementNode, RelTypes.IS_OF_TYPE); } } } }
	 */

	private IJavaElement getIJavaElement(MethodDeclaration methodDeclaration) {
		IMethodBinding resolveBinding = methodDeclaration.resolveBinding();
		if (resolveBinding != null) {
			IMethod methodJavaElement = (IMethod) resolveBinding.getJavaElement();
			return methodJavaElement;
		}
		return null;
	}

	@Override
	public boolean visit(FieldDeclaration fieldDeclaration) {
		List fragments = fieldDeclaration.fragments();
		for (Object object : fragments) {
			if (object instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) object;
				IVariableBinding resolveBinding = variableDeclarationFragment.resolveBinding();
				if (resolveBinding != null) {
					IJavaElement iJavaElement = resolveBinding.getJavaElement();
					if (iJavaElement != null) {
						int modifiers = fieldDeclaration.getModifiers();
						Node field = db.createIJavaElementNode(iJavaElement, commit, commitID, added, removed);
						if (Modifier.isProtected(modifiers))
							field.setProperty("protected", true);
						if (Modifier.isPublic(modifiers))
							field.setProperty("public", true);
						if (Modifier.isPrivate(modifiers))
							field.setProperty("private", true);
					}
				}
			}
		}
		return true;
	}

	// check if getter or setter
	@Override
	public boolean visit(MethodDeclaration methodDeclaration) {

		methodMetrics(methodDeclaration);

		if (methodDeclaration.isConstructor())
			return true;
		System.out.println("method body -- " + methodDeclaration.getBody().toString());
		if (methodDeclaration.getBody() == null || methodDeclaration.getBody().statements().size() != 1)
			return true;
		if (methodDeclaration.getName().getIdentifier().startsWith("get")) {
			Object object = methodDeclaration.getBody().statements().get(0);
			if (!(object instanceof ReturnStatement))
				return true;
			else {
				ReturnStatement returnStatement = (ReturnStatement) object;
				Expression expression = returnStatement.getExpression();
				if (expression == null)
					return true; // method name starts with get but returns
				// null
				if (isObjectFieldAccess(expression)) {
					if (methodDeclaration != null) {
						IMethodBinding methodBinding = methodDeclaration.resolveBinding();
						if (methodBinding != null) {
							Node method = db.createIJavaElementNode(methodBinding.getJavaElement(), commit, commitID, added, removed);
							method.setProperty("get", true);
						}
					}
				}

			}
		} else if (methodDeclaration.getName().getIdentifier().startsWith("set")) {
			Object object = methodDeclaration.getBody().statements().get(0);
			if (!(object instanceof Assignment) && !(object instanceof ExpressionStatement))
				return true;
			if (object instanceof ExpressionStatement)
				object = ((ExpressionStatement) object).getExpression();
			if (!(object instanceof Assignment))
				return true;
			Assignment assignment = (Assignment) object;
			Expression leftExpression = assignment.getLeftHandSide();
			if (isObjectFieldAccess(leftExpression) && isParameterAccess(assignment.getRightHandSide())) {
				IMethodBinding methodBinding = methodDeclaration.resolveBinding();
				if (methodBinding != null) {
					IJavaElement methodJavaElement = methodBinding.getJavaElement();
					if (methodJavaElement != null) {
						Node method = db.createIJavaElementNode(methodJavaElement, commit, commitID, added, removed);
						method.setProperty("get", true);
					}
				}
			}
		}
		return true;
	}

	private void methodMetrics(MethodDeclaration methodDeclaration) {
		IJavaElement iJavaElement = getIJavaElement(methodDeclaration);
		if (iJavaElement != null) {

			MetricVisitor metricVisitor = new MetricVisitor();
			metricVisitor.process(methodDeclaration);
			int cyclomaticComplexity = metricVisitor.getCyclomaticComplexity();
			int numberOfStatements = metricVisitor.getNumberOfStatements();
			Node method = db.createIJavaElementNode(iJavaElement, commit, commitID, added, removed);
			method.setProperty("cyclomaticComplexity", cyclomaticComplexity);
			method.setProperty("numberOfStatements", numberOfStatements);

			int modifiers = methodDeclaration.getModifiers();
			if (Modifier.isProtected(modifiers))
				method.setProperty("protected", true);
			if (Modifier.isPublic(modifiers))
				method.setProperty("public", true);
			if (Modifier.isPrivate(modifiers))
				method.setProperty("private", true);

			if (currentTypeResolvedBinding != null) {
				IMethodBinding resolveBinding2 = methodDeclaration.resolveBinding();
				ITypeBinding superclass = currentTypeResolvedBinding.getSuperclass();
				if (superclass != null) {
					IMethodBinding[] superDeclaredMethods = superclass.getDeclaredMethods();
					for (IMethodBinding iMethodBinding : superDeclaredMethods) {
						if (resolveBinding2.overrides(iMethodBinding)) {
							method.setProperty("overiding", true);
						}
					}
				}
			}
		}
	}

	private boolean isParameterAccess(Expression rightHandSide) {
		return rightHandSide.getNodeType() == Expression.SIMPLE_NAME;
	}

	private boolean isObjectFieldAccess(Expression leftExpression) {
		if (leftExpression.getNodeType() == Expression.FIELD_ACCESS) {
			FieldAccess fieldAccess = (FieldAccess) leftExpression;
			if (fieldAccess.getExpression() instanceof ThisExpression) {
				return true;
			}
		} else if (leftExpression.getNodeType() == Expression.SIMPLE_NAME) {
			return true;
		}
		return false;
	}

	@Override
	public boolean visit(TypeDeclaration td) {
		currentTypeResolvedBinding = td.resolveBinding();
		ITypeBinding superClassBinding = currentTypeResolvedBinding.getSuperclass();
		if (superClassBinding != null && superClassBinding.isFromSource()) {
			if (currentTypeResolvedBinding.getJavaElement() != null & superClassBinding.getJavaElement() != null) {
				Node subclass = db.createIJavaElementNode(currentTypeResolvedBinding.getJavaElement(), commit, commitID, added, removed);
				Node superClass = db.createIJavaElementNode(superClassBinding.getJavaElement(), commit, commitID, added, removed);
				subclass.createRelationshipTo(superClass, RelTypes.EXTENDS);
			}
		}
		return true;
	}

	@Override
	public boolean visit(FieldAccess node) {
		IVariableBinding resolveFieldBinding = node.resolveFieldBinding();
		if (resolveFieldBinding != null && resolveFieldBinding.getDeclaringClass() != null && resolveFieldBinding.getDeclaringClass().isFromSource()) {
			addFieldAccess(node, resolveFieldBinding);
			return false;
		}
		return true;
	}

	private void addFieldAccess(Expression node, IVariableBinding resolveFieldBinding) {
		if (resolveFieldBinding.getDeclaringClass().isFromSource()) {
			MethodDeclaration containingMethodDeclaration = findContaingMethod(node);
			if (containingMethodDeclaration != null) {
				IJavaElement containingIMethod = getIJavaElement(containingMethodDeclaration);
				if (containingIMethod != null) {
					IJavaElement accessedIField = resolveFieldBinding.getJavaElement();
					if (accessedIField != null) {
						Node methodIJavaElementNode = db.createIJavaElementNode(containingIMethod, commit, commitID, added, removed);
						Node accessedIFieldElementNode = db.createIJavaElementNode(accessedIField, commit, commitID, added, removed);
						System.out.println(containingIMethod.getElementName() + " -> " + accessedIField.getElementName());
						methodIJavaElementNode.createRelationshipTo(accessedIFieldElementNode, RelTypes.ACCESSES);
					}
				}
			}
		}
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		IVariableBinding resolveFieldBinding = node.resolveFieldBinding();
		addFieldAccess(node, resolveFieldBinding);
		return true;
	}

	@Override
	public boolean visit(SimpleName simpleName) {
		IBinding resolveBinding = simpleName.resolveBinding();
		if (resolveBinding != null) {
			IJavaElement javaElement = resolveBinding.getJavaElement();
			if (javaElement != null) {
				int elementType = javaElement.getElementType();
				if (resolveBinding != null && javaElement != null && elementType == IJavaElement.FIELD) {
					addFieldAccess(simpleName, (IVariableBinding) resolveBinding);
				}
				if (resolveBinding != null && javaElement != null && elementType == IJavaElement.METHOD && !(simpleName.getParent() instanceof MethodDeclaration)) {
					addMethodCall(simpleName, (IMethodBinding) resolveBinding);
				}
			}
		}
		return true;
	}

	private void addMethodCall(Expression simpleName, IMethodBinding resolveBinding) {
		if (resolveBinding.getDeclaringClass() != null && resolveBinding.getDeclaringClass().isFromSource()) {
			MethodDeclaration containingMethodDeclaration = findContaingMethod(simpleName);
			if (containingMethodDeclaration != null) {
				IJavaElement containingIMethod = getIJavaElement(containingMethodDeclaration);
				if (containingIMethod != null) {
					IJavaElement calledImethod = resolveBinding.getJavaElement();
					if (calledImethod != null) {
						Node methodIJavaElementNode = db.createIJavaElementNode(containingIMethod, commit, commitID, added, removed);
						Node accessedIFieldElementNode = db.createIJavaElementNode(calledImethod, commit, commitID, added, removed);
						methodIJavaElementNode.createRelationshipTo(accessedIFieldElementNode, RelTypes.CALLS);
					}
				}
			}
		}
	}

	private MethodDeclaration findContaingMethod(ASTNode node) {
		if (node == null)
			return null;
		if (node.getNodeType() == ASTNode.METHOD_DECLARATION) {
			return (MethodDeclaration) node;
		} else
			return findContaingMethod(node.getParent());
	}

}
