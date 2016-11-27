package org.lrg.outcode.views.browseractions;

import java.io.File;
import java.util.Iterator;

import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.ui.PlatformUI;
import org.lrg.outcode.builder.ModelVistor;

public class OpenCommitDiff implements IBrowserAction {

	@SuppressWarnings("restriction")
	@Override
	public String onMessage(String messageName, Object[] arg) { // will be OpenCommitDiff

		File gitDir = new RepoDirFinder().findRepoDir();
		RepositoryBuilder builder = new RepositoryBuilder();
		Repository repository;
		try {
			ModelVistor.parsedLOC = 0;

			repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
			Git git = new Git(repository);
			RevWalk walk = new RevWalk(repository);
			RevCommit commit1 = null;
			RevCommit commit2 = null;
			Iterable<RevCommit> logs = git.log().call();
			Iterator<RevCommit> i = logs.iterator();

			// move this to
			while (i.hasNext()) {
				RevCommit next = i.next();
				commit1 = next;
				if (commit1.getName().equals(arg[1])) {
					break;
				}
			}
			logs = git.log().call();
			i = logs.iterator();
			while (i.hasNext()) {
				RevCommit next = i.next();
				commit2 = next;
				if (commit2.getName().equals(arg[2])) {
					break;
				}
			}

			// gitdir location vs project location

			System.out.println("git dir " + gitDir);
			String absoluteProjectPath = new RepoDirFinder().findProjectDir().getAbsolutePath();
			System.out.println("project dir " + absoluteProjectPath);
			String difference = "";
			difference = absoluteProjectPath.replaceFirst(cutLastDotGit(gitDir), "");
			if (difference.length() > 0) {
				difference += "/";
				if (difference.startsWith("/"))
					difference = difference.replaceFirst("/", "");
			}

			CompareUtils.openInCompare(commit1, commit2, difference + arg[3].toString(), difference + arg[3].toString(), repository, PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage());
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

	private String cutLastDotGit(File gitDir) {
		return gitDir.getAbsolutePath().substring(0, gitDir.getAbsolutePath().length() - "/.git".length());
	}

}
