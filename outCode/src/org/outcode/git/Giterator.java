package org.outcode.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.lrg.outcode.CountdownTimer;
import org.lrg.outcode.IHindsight;
import org.lrg.outcode.activator.GraphDB;
import org.lrg.outcode.builder.ModelVistor;
import org.lrg.outcode.visitors.FeatureEnvy;
import org.lrg.outcode.visitors.GodClass;
import org.lrg.outcode.visitors.IntensiveCoupling;
import org.lrg.outcode.visitors.RefusedParentBequest;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;

public class Giterator extends AbstractHandler {

	private DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE);;

	public static final class NullProgressMonitorExtension extends NullProgressMonitor {
		private boolean isDone = false;

		@Override
		public void done() {
			isDone = true;
			super.done();
		}

		public boolean isDone() {
			return isDone;
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job j = new Job("j") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				walkRepo(monitor);
				return Status.OK_STATUS;
			}
		};
		j.schedule();

		return Status.OK_STATUS;
	}

	private void walkRepo(IProgressMonitor moni) {
		File gitDir = findRepoDir();
		if (gitDir == null)
			return;

		RepositoryBuilder builder = new RepositoryBuilder();
		Repository repository;
		try {
			ModelVistor.parsedLOC = 0;
			CountdownTimer.start("iterating");
			repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
			Git git = new Git(repository);
			RevWalk walk = new RevWalk(repository);
			RevCommit commit = null;
			Iterable<RevCommit> logs = git.log().call();
			Iterator<RevCommit> i = logs.iterator();
			ArrayList<RevCommit> revCommits = new ArrayList<RevCommit>();
			while (i.hasNext()) {
				RevCommit next = i.next();
				revCommits.add(0, next);
			}
			int k = 0;
			int noK = 0;
			boolean isEclipseProject = false;
			SubMonitor monitor = SubMonitor.convert(moni, revCommits.size());
			for (RevCommit revCommit : revCommits) {
				CountdownTimer.start("parseCommit");
				commit = walk.parseCommit(revCommit);
				CountdownTimer.stop("parseCommit");

				System.out.println("parsing commit with message " + revCommit.getFullMessage() + new Date(revCommit.getCommitTime()).toString());
				if (!isEclipseProject && addedDotProjectFile(repository, walk, commit))
					isEclipseProject = true;

				if (isEclipseProject) {
					CheckoutCommand checkout = git.checkout();
					try {
						CountdownTimer.start("call");
						checkout.setName(commit.getName()).call();
						CountdownTimer.stop("call");
						CheckoutResult result = checkout.getResult();

						if (result.getStatus() == CheckoutResult.Status.OK) {
							refreshWs();
							if (k == 0) {
								RevTree tree = commit.getTree();

								System.out.println("modified " + tree);
								System.out.println("commit name " + commit.getName() + " " + commit.getId().name());
								new ModelVistor(commit.getCommitTime(), commit.getName(), repository).visitIJavaProject(ResourcesPlugin.getWorkspace().getRoot().getProjects()[0], null);
								runMethodDetectionStrategies(commit.getCommitTime() + "000", true);
								runClassDetectionStrategies(commit.getCommitTime() + "000");
							} else {
								if (commit.getParentCount() > 0) {
									RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
									ByteArrayOutputStream out = new ByteArrayOutputStream();
									df = new DiffFormatter(out);
									df.setRepository(repository);
									df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
									df.setDetectRenames(true);
									List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
									String version = commit.getCommitTime() + "000";
									System.out.println("------- version " + new Date(Long.parseLong(version)).toString() + " -------");
									new ModelVistor(commit.getCommitTime(), commit.getName(), repository).visitIJavaProject(ResourcesPlugin.getWorkspace().getRoot().getProjects()[0], gitDir.getAbsolutePath().replace("/.git", "/"), diffs, version,
											commit.getName(), null);
									runMethodDetectionStrategies(version, false);
									runClassDetectionStrategies(version);
								}
							}
							k++;
						} else {
							System.err.println("checkout failed " + result.getStatus());
						}
					} catch (Exception ex) {
						CountdownTimer.stop("call");
						ex.printStackTrace();
					}
				} else
					noK++;

				if (monitor.isCanceled())
					break;
				monitor.worked(1);
			}
			git.close();
			monitor.done();
			System.out.println("ka = " + k);
			System.out.println("noK = " + noK);

			CountdownTimer.stop("iterating");
			CountdownTimer.printAndReset("call");
			CountdownTimer.printAndReset("parseCommit");
			CountdownTimer.printAndReset("iterating");
			System.out.println("ka = " + k);
			System.out.println("noK = " + noK);
			System.out.println("total loc parsed " + ModelVistor.parsedLOC);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoHeadException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	private File findRepoDir() {
		IProject iProject = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0];
		IPath location = iProject.getLocation();

		if (location.append("/.git").toFile().exists())
			return location.append(".git").toFile();
		while (location.segmentCount() > 0 && !location.append("/.git").toFile().exists())
			location = location.removeLastSegments(1);

		if (location.append("/.git").toFile().exists()) {
			IPath finalLocation = location.append("/.git");
			System.out.println("repo found " + finalLocation.toPortableString());
			return finalLocation.toFile();
		}
		return null;
	}

	private boolean addedDotProjectFile(Repository repository, RevWalk walk, RevCommit commit) {
		try {

			ObjectId parentTree = repository.resolve("HEAD@{0}");
			if (commit.getParentCount() > 0) {
				RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
				parentTree = parent.getTree();
			}

			df.setRepository(repository);
			df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
			df.setDetectRenames(true);

			List<DiffEntry> diffs = df.scan(null, commit.getTree());
			for (DiffEntry diff : diffs) {
				String replaceFirst = diff.getNewPath();// .substring(0,
				// diff.getNewPath().length()
				// ); // removes
				replaceFirst = replaceFirst.substring(replaceFirst.indexOf("/") + 1, replaceFirst.length());
				if (replaceFirst.endsWith(".project"))

					return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private void refreshWs() throws CoreException {
		// System.out.println("starting refresh");
		NullProgressMonitorExtension nullProgressMonitorExtension = new NullProgressMonitorExtension();

		ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, nullProgressMonitorExtension);
		while (!nullProgressMonitorExtension.isDone()) {
			System.out.println("sleeping a sec");
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// System.out.println("refresh done");
	}

	public static void runMethodDetectionStrategies(String commit, boolean b) {
		GraphDatabaseService dbService = GraphDB.instance.getDB();
		Transaction tx = dbService.beginTx();
		try {
			FeatureEnvy featureEnvy = new FeatureEnvy(b);
			IntensiveCoupling intensiveCoupling = new IntensiveCoupling();
			ResourceIterator<Node> allArticles = dbService.findNodes(Label.label("Method"), IHindsight.COMMIT, commit);
			while (allArticles.hasNext()) {
				Node node = allArticles.next();
				featureEnvy.findFeatureEnvy(node);
				intensiveCoupling.findIntensiveCoupling(node);
			}
			allArticles.close();
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tx.close();
		}
	}

	public static void runClassDetectionStrategies(String commit) {
		GraphDatabaseService dbService = GraphDB.instance.getDB();
		Transaction tx = dbService.beginTx();
		try {
			GodClass godClass = new GodClass();
			RefusedParentBequest refusedParentBequest = new RefusedParentBequest();

			ResourceIterator<Node> allArticles = dbService.findNodes(Label.label("Type"), IHindsight.COMMIT, commit);
			while (allArticles.hasNext()) {
				Node node = allArticles.next();
				godClass.findGodClasses(node);
				refusedParentBequest.findRefusedParentBequest(node);
			}
			allArticles.close();
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tx.close();
		}

	}
}
