package org.lrg.outcode.builder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.lrg.outcode.activator.GraphDB;
import org.lrg.outcode.builder.ast.OutCodeVisitor;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.lrg.outcode.builder.detection.IDetectionStrategy;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.osgi.framework.BundleException;

public class ModelVistor {

	private String commit;
	private String commitID;

	public static int parsedLOC = 0;
	private Repository repository;

	public static enum OPERATIONS {
		INITIAL, ADD, MODIFY, DELETE
	}

	public ModelVistor(int i, String commitID, Repository repository) {
		this.repository = repository;
		this.commit = new Integer(i).toString() + "000";
		this.commitID = commitID;
	}

	private GraphDatasource db = GraphDatasource.INSTANCE;
	private ASTParser theParser = ASTParser.newParser(AST.JLS8);
	private HashMap<Integer, HashSet<IDetectionStrategy>> listeners = new HashMap<Integer, HashSet<IDetectionStrategy>>();
	private OutCodeVisitor outCodeVisitor;

	public void visitIJavaProject(IProject project, OPERATIONS op) throws CoreException, JavaModelException {
		System.out.println("Working in project " + project.getName());
		try {
			JavaCore.getPlugin().getBundle().stop();
			JavaCore.getPlugin().getBundle().start();
		} catch (BundleException e) {
			e.printStackTrace();
		}
		// check if we have a Java project
		if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
			IJavaProject javaProject = JavaCore.create(project);
			visitIPackageFragments(javaProject, op);
		}
	}

	public void configureListener(Integer elementType, IDetectionStrategy iDetectionStrategy) {
		if (listeners.get(elementType) == null)
			listeners.put(elementType, new HashSet<IDetectionStrategy>());
		listeners.get(elementType).add(iDetectionStrategy);
	}

	private void visitIPackageFragments(IJavaProject javaProject, OPERATIONS op) throws JavaModelException {
		IPackageFragment[] packages = javaProject.getPackageFragments();
		GraphDatabaseService dbService = GraphDB.instance.getDB();
		Transaction tx = dbService.beginTx();
		try {
			db.addProjectToDb(javaProject, commit, commitID);
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tx.close();
		}
		for (IPackageFragment mypackage : packages) {
			if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
				System.out.println("Package " + mypackage.getElementName());
				tx = dbService.beginTx();
				try {
					db.addNodeToDB(javaProject, mypackage, commit, commitID, 0, 0);
					tx.success();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					tx.close();
				}
				visitICompilationUnits(mypackage, op);
			}
		}
	}

	private void visitICompilationUnits(IPackageFragment mypackage, OPERATIONS op) throws JavaModelException {

		for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
			GraphDatabaseService dbService = GraphDB.instance.getDB();
			Transaction tx = dbService.beginTx();

			try {
				parseCompilationUnit(unit, op, 0, 0);
				db.addNodeToDB(mypackage, unit, commit, commitID, 0, 0);
				visitITypes(unit, op, 0, 0);
				tx.success();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				tx.close();
			}
		}
	}

	private void parseCompilationUnit(ICompilationUnit unit, OPERATIONS op, int added, int removed) {
		try {
			parsedLOC += unit.getSource().split("\\r?\\n").length;
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		outCodeVisitor = new OutCodeVisitor(commit, commitID, added, removed);
		theParser.setSource(unit);
		theParser.setResolveBindings(true);
		ASTNode entireAST = theParser.createAST(new NullProgressMonitor());

		entireAST.accept(outCodeVisitor);
	}

	private void visitNextVersionITypes(Node modifiedCompilationUnitNode, ICompilationUnit unit, OPERATIONS op, String version, int addedLOC, int removedLOC) throws JavaModelException {
		IType[] allTypes = unit.getAllTypes();
		for (IType type : allTypes) {
			Node latestVersionOfIType = db.addNextVersionNodeToDB(unit, type, version, commitID, addedLOC, removedLOC);
			addContanmentRel(modifiedCompilationUnitNode, latestVersionOfIType);
			visitIMethods(latestVersionOfIType, type, op, version, addedLOC, removedLOC);
			visitIFields(latestVersionOfIType, type, op, version, addedLOC, removedLOC);
		}
	}

	private void visitIMethods(Node latestVersionOfIType, IType type, OPERATIONS op, String version, int addedLOC, int removedLOC) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (IMethod method : methods) {
			Node latestVersionOfIMethod = db.addNextVersionNodeToDB(type, method, version, commitID, addedLOC, removedLOC);
			addContanmentRel(latestVersionOfIType, latestVersionOfIMethod);
		}
	}

	private void addContanmentRel(Node parent, Node child) {
		if (child != null && parent != null) {
			parent.createRelationshipTo(child, RelTypes.CONTAINS);
			child.createRelationshipTo(parent, RelTypes.PARENT);
		}
	}

	private void visitIFields(Node latestVersionOfIType, IType type, OPERATIONS op, String version, int addedLOC, int removedLOC) throws JavaModelException {
		IField[] methods = type.getFields();
		for (IField field : methods) {
			Node latestVersionOfIField = db.addNextVersionNodeToDB(type, field, version, commitID, addedLOC, removedLOC);
			addContanmentRel(latestVersionOfIType, latestVersionOfIField);
		}
	}

	private void visitITypes(ICompilationUnit unit, OPERATIONS op, int addedLOC, int removedLOC) throws JavaModelException {
		IType[] allTypes = unit.getAllTypes();
		for (IType type : allTypes) {
			db.addNodeToDB(unit, type, commit, commitID, addedLOC, removedLOC);
			visitIMethods(type, op, addedLOC, removedLOC);
			visitIFields(type, op, addedLOC, removedLOC);
		}
	}

	private void visitIMethods(IType type, OPERATIONS op, int addedLOC, int removedLOC) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (IMethod method : methods) {
			db.addNodeToDB(type, method, commit, commitID, addedLOC, removedLOC);
		}
	}

	private void visitIFields(IType type, OPERATIONS op, int addedLOC, int removedLOC) throws JavaModelException {
		IField[] fileds = type.getFields();
		for (IField field : fileds) {
			db.addNodeToDB(type, field, commit, commitID, addedLOC, removedLOC);
		}
	}

	@SuppressWarnings("restriction")
	public void visitIJavaProject(IProject iProject, String repoPath, List<DiffEntry> diffs, String version, String commitID) {
		GraphDatabaseService dbService = GraphDB.instance.getDB();
		Transaction tx = dbService.beginTx();
		try {
			JavaModelManager.getJavaModelManager().shutdown();
			JavaModelManager.getJavaModelManager().startup();
			Set<IPackageFragment> updateContainedElements = new HashSet<IPackageFragment>();
			Set<IJavaElement> skipAlreadyAdded = new HashSet<IJavaElement>();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for (DiffEntry diffEntry : diffs) {
				String absolutePath = repoPath + diffEntry.getNewPath();
				String pathFromProject = absolutePath.replaceAll("\\\\", "/").replaceFirst(iProject.getLocation().toPortableString(), "");
				IFile file = iProject.getFile(new Path(pathFromProject));
				file.refreshLocal(IResource.DEPTH_INFINITE, null);
				if (file.exists()) {
					IJavaProject javaProject = JavaCore.create(iProject);
					try {
						ICompilationUnit iCompilationUnit = findICompilationUnit(file, javaProject);
						if (iCompilationUnit != null) {

							DiffFormatter df = new DiffFormatter(out);
							df.setRepository(repository);
							df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
							df.setDetectRenames(true);

							int addedLOC = 0;
							int removedLOC = 0;
							try {
								// Format a patch script for one file entry.
								df.setContext(0);
								df.format(diffEntry);
								String formattedDiff = out.toString();
								addedLOC = countLinesThatStartWith("+", formattedDiff);
								removedLOC = countLinesThatStartWith("-", formattedDiff);
								out.reset();
							} catch (IOException e) {
								e.printStackTrace();
							} finally {
								df.close();
							}

							if (diffEntry.getChangeType() == ChangeType.MODIFY) {
								System.out.println("---modify---- icompilationunint " + iCompilationUnit.getElementName());
								Node latestVersionOfICompilationUnit = db.addModifiedVersion(iCompilationUnit, OPERATIONS.MODIFY, version);
								parseCompilationUnit(iCompilationUnit, OPERATIONS.MODIFY, addedLOC, removedLOC);
								visitNextVersionITypes(latestVersionOfICompilationUnit, iCompilationUnit, OPERATIONS.MODIFY, version, addedLOC, removedLOC);
							} else if (diffEntry.getChangeType() == ChangeType.ADD || diffEntry.getChangeType() == ChangeType.COPY) {
								/*
								 * A new file is added with this commit. Make a new version of the package because now it's different.
								 */
								System.out.println("---add---- icompilationunint " + iCompilationUnit.getElementName());
								IJavaElement iPackageFragment = iCompilationUnit.getParent();
								db.addNextVersionNodeToDB(javaProject, iPackageFragment, version, commitID, addedLOC, removedLOC);
								parseCompilationUnit(iCompilationUnit, OPERATIONS.ADD, addedLOC, removedLOC);
								db.addNodeToDB(iPackageFragment, iCompilationUnit, version, commitID, addedLOC, removedLOC);
								visitITypes(iCompilationUnit, OPERATIONS.ADD, addedLOC, removedLOC);
								skipAlreadyAdded.add(iCompilationUnit);
								updateContainedElements.add((IPackageFragment) iPackageFragment);
							} else if (diffEntry.getChangeType() == ChangeType.DELETE) {
								IJavaElement iPackageFragment = iCompilationUnit.getParent();
								updateContainedElements.add((IPackageFragment) iPackageFragment);
							}
						}
					} catch (JavaModelException e) {
						e.printStackTrace();
					} catch (ClassCastException cce) {
						cce.printStackTrace();
					}

				} else {
					System.out.println("file not found " + repoPath + diffEntry.getNewPath());
				}
			}

			for (IPackageFragment iJavaElement : updateContainedElements) {
				IJavaElement[] children = iJavaElement.getChildren();
				for (IJavaElement child : children) {
					if (skipAlreadyAdded.contains(child))
						continue;
					Node parentNode = db.findLatestNodeForElementID(iJavaElement);
					Node childNode = db.findLatestNodeForElementID(child);
					addContanmentRel(parentNode, childNode);
				}
			}
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tx.close();
		}
	}

	private int countLinesThatStartWith(String plusMinus, String formattedDiff) {
		Pattern pattern = Pattern.compile("\\n" + "\\" + plusMinus);
		Matcher matcher = pattern.matcher(formattedDiff);
		int count = 0;
		while (matcher.find())
			count++;
		return count--;
	}

	private ICompilationUnit findICompilationUnit(IFile file, IJavaProject javaProject) throws JavaModelException {
		IPackageFragmentRoot[] allPackageFragmentRoots = javaProject.getAllPackageFragmentRoots();
		for (IPackageFragmentRoot iPackageFragmentRoot : allPackageFragmentRoots) {
			if (file.getName().endsWith(".java")) {
				ICompilationUnit result = (ICompilationUnit) javaProject.findElement(file.getFullPath().makeRelativeTo(iPackageFragmentRoot.getPath()));
				if (result != null)
					return result;
			}
		}
		return null;
	}

}