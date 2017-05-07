package org.lrg.outcode.eclipse.handlers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RenameCallback;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.lrg.outcode.CountdownTimer;
import org.lrg.outcode.builder.ModelVistor;
import org.lrg.outcode.builder.db.GraphDatasource;
import org.outcode.git.Giterator.NullProgressMonitorExtension;

public class CurrentFileEvolution extends AbstractHandler {

	private final RenameTracker renameTracker = new RenameTracker();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		ISelection selection = activePage.getSelection();
		IJavaElement elem = null;
		if (selection instanceof ITextSelection) {

			elem = JavaUI.getEditorInputJavaElement(activePage.getActiveEditor().getEditorInput());

			if (elem instanceof ICompilationUnit) {
				final ICompilationUnit unit = (ICompilationUnit) elem;
				File gitDir = findRepoDir(unit);
				if (gitDir == null)
					return null;

				RepositoryBuilder builder = new RepositoryBuilder();
				Repository repository;

				ModelVistor.parsedLOC = 0;
				CountdownTimer.start("iterating");
				try {
					repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
					final RepositoryMapping map = RepositoryMapping.getMapping(unit.getResource());
					repository = map.getRepository();
					RevWalk walk = new RevWalk(repository);
					walk.sort(RevSort.COMMIT_TIME_DESC, true);
					walk.sort(RevSort.BOUNDARY, true);
					AnyObjectId headId = resolveHead(repository, true);
					if (headId == null) {
						walk.close();
						return null;
					}
					FilterPath path = buildFilterPaths(unit.getResource(), repository);
					walk.markStart(walk.parseCommit(headId));

					final RevWalk fileWalker = createFileWalker(walk, repository, path);

					RevCommit next2 = fileWalker.next();
					ArrayList<RevCommit> revCommits = new ArrayList<RevCommit>();
					while (next2 != null) {
						System.out.println("commit time " + next2.getCommitTime() + " commit " + next2.name());
						revCommits.add(next2);
						next2 = fileWalker.next();
					}

					Job j = new Job("j") {

						@Override
						protected IStatus run(IProgressMonitor monitor) {
							extractModelToDb(monitor, revCommits, map.getRepository(), unit);
							try {
								new EntityAccessesEvolution().execute(unit);
							} catch (ExecutionException e) {
								e.printStackTrace();
							}
							return Status.OK_STATUS;
						}
					};
					j.schedule();

				} catch (IOException e) {
					e.printStackTrace();
				}

			}

		}
		return null;
	}

	private void refreshWs() throws CoreException {
		NullProgressMonitorExtension nullProgressMonitorExtension = new NullProgressMonitorExtension();
		ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, nullProgressMonitorExtension);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		while (!nullProgressMonitorExtension.isDone()) {
			System.out.println("sleeping a sec");
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private void extractModelToDb(IProgressMonitor moni, ArrayList<RevCommit> revCommits, Repository repository, ICompilationUnit unit) {
		File gitDir = repository.getDirectory();
		SubMonitor monitor = SubMonitor.convert(moni, revCommits.size());
		RevCommit commit = null;
		Git git = new Git(repository);
		RevWalk walk = new RevWalk(repository);
		int k = 0;
		int noK = 0;

		for (RevCommit revCommit : revCommits) {
			try {
				CountdownTimer.start("parseCommit");
				commit = walk.parseCommit(revCommit);
				CountdownTimer.stop("parseCommit");

				System.out.println("parsing commit with message " + revCommit.getName() + " " + revCommit.getFullMessage() + new Date(revCommit.getCommitTime()).toString());
				String path = renameTracker.getPath(commit, unit.getResource().getFullPath().toPortableString());
				if (GraphDatasource.INSTANCE.createdNewCommitNode(commit.getName() + path)) {
					CheckoutCommand checkout = git.checkout();
					try {
						CountdownTimer.start("call");
						checkout.setName(commit.getName()).call();
						CountdownTimer.stop("call");
						CheckoutResult result = checkout.getResult();
						IProject javaProject = unit.getResource().getProject();

						if (result.getStatus() == CheckoutResult.Status.OK) {
							refreshWs();
							if (k == 0) {
								RevTree tree = commit.getTree();

								System.out.println("modified " + tree);
								System.out.println("commit name " + commit.getName() + " " + commit.getId().name());

								new ModelVistor(commit.getCommitTime(), commit.getName(), repository).visitIJavaProject(javaProject, path);
							} else {
								if (commit.getParentCount() > 0) {
									RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
									ByteArrayOutputStream out = new ByteArrayOutputStream();
									DiffFormatter df = new DiffFormatter(out);
									df.setRepository(repository);
									df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
									df.setDetectRenames(true);
									List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
									df.close();
									String version = commit.getCommitTime() + "000";
									System.out.println("------- version " + new Date(Long.parseLong(version)).toString() + " -------");
									String absolutepath = gitDir.getAbsolutePath().replace("/.git", "/");
									new ModelVistor(commit.getCommitTime(), commit.getName(), repository).visitIJavaProject(javaProject, absolutepath, diffs, version, commit.getName(), path);
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
			} catch (Exception e) {
				e.printStackTrace();
			}
			git.close();
			monitor.done();
		}
		walk.close();
	}

	private RevWalk createFileWalker(RevWalk walk, Repository db, FilterPath paths) {
		TreeFilter followFilter = createFollowFilterFor(db, paths.getPath());
		walk.setTreeFilter(followFilter);
		walk.sort(RevSort.REVERSE);
		walk.setRevFilter(renameTracker.getFilter());
		return walk;
	}

	/**
	 * Creates a filter for the given files, will make sure that renames/copies of all files will be followed.
	 * 
	 * @param paths
	 *            the list of files to follow, must not be <code>null</code> or empty
	 * @return the ORed list of {@link FollowFilter follow filters}
	 */
	private TreeFilter createFollowFilterFor(Repository db, String paths) {
		if (paths == null || paths.isEmpty())
			throw new IllegalArgumentException("paths must not be null nor empty"); //$NON-NLS-1$
		DiffConfig diffConfig = db.getConfig().get(DiffConfig.KEY);
		return createFollowFilter(paths, diffConfig);
	}

	private FollowFilter createFollowFilter(String path, DiffConfig diffConfig) {
		FollowFilter followFilter = FollowFilter.create(path, diffConfig);
		followFilter.setRenameCallback(new RenameCallback() {
			@Override
			public void renamed(DiffEntry entry) {
				renameTracker.getCallback().renamed(entry);
			}
		});
		return followFilter;
	}

	private FilterPath buildFilterPaths(final IResource inResource, final Repository db) throws IllegalStateException {
		if (inResource != null) {
			final RepositoryMapping map = RepositoryMapping.getMapping(inResource);
			if (map != null) {
				if (db != map.getRepository())
					throw new IllegalStateException(UIText.RepositoryAction_multiRepoSelection);

				final String path = map.getRepoRelativePath(inResource);
				if (path != null && path.length() > 0)
					return new FilterPath(path, inResource.getType() == IResource.FILE);
			}
		}
		return null;
	}

	private AnyObjectId resolveHead(Repository db, boolean acceptNull) {
		AnyObjectId headId;
		try {
			headId = db.resolve(Constants.HEAD);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		if (headId == null && !acceptNull)
			throw new IllegalStateException("error parsing head");
		return headId;
	}

	private File findRepoDir(ICompilationUnit unit) {

		IProject iProject = unit.getJavaProject().getProject();
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

	/**
	 * This class defines a couple that associates two pieces of information: the file path, and whether it is a regular file (or a directory).
	 */
	private static class FilterPath {

		private String path;

		private boolean regularFile;

		public FilterPath(String path, boolean regularFile) {
			super();
			this.path = path;
			this.regularFile = regularFile;
		}

		/** @return the file path */
		public String getPath() {
			return path;
		}

		/**
		 * @return <code>true</code> if the file is a regular file, and <code>false</code> otherwise (directory, project)
		 */
		public boolean isRegularFile() {
			return regularFile;
		}

		/**
		 * In {@link FilterPath} class, equality is based on {@link #getPath path} equality.
		 */
		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof FilterPath))
				return false;
			FilterPath other = (FilterPath) obj;
			if (path == null)
				return other.path == null;
			return path.equals(other.path);
		}

		@Override
		public int hashCode() {
			if (path != null)
				return path.hashCode();
			return super.hashCode();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("Path: "); //$NON-NLS-1$
			builder.append(getPath());
			builder.append("regular: "); //$NON-NLS-1$
			builder.append(isRegularFile());

			return builder.toString();
		}
	}

}
