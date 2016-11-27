package org.lrg.outcode.views.browseractions;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

public class RepoDirFinder {
	public File findRepoDir() {
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

	public File findProjectDir() {
		IProject iProject = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0];
		return new File(iProject.getLocation().toPortableString());
	}
}
