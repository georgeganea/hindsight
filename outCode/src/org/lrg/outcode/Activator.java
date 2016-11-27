package org.lrg.outcode;

import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.lrg.outcode.activator.GraphDB;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.lrg.OutCode"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		GraphDB.instance.startDB();
		super.start(context);

		startHTMLServer();
		plugin = this;
	}

	private void startHTMLServer() {

		Job startServer = new Job("server start") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				// Create a basic Jetty server object that will listen on port 8080. Note thatthis to port 0
				// then a randomly available port will be assigned that you can either look in the logs for the port,
				// or programmatically obtain it for use in test cases.
				Server server = new Server(8081);

				// Create the ResourceHandler. It is the object that will actually handle the request for a given file. It is
				// a Jetty Handler object so it is suitable for chaining with other handlers as you will see in other examples.
				ResourceHandler resource_handler = new ResourceHandler();

				// Configure the ResourceHandler. Setting the resource base indicates where the files should be served out of.
				// In this example it is the current directory but it can be configured to anything that the jvm has access to.
				resource_handler.setDirectoriesListed(true);
				resource_handler.setWelcomeFiles(new String[] { "index.html" });
				Bundle bundle = Platform.getBundle(PLUGIN_ID);
				Path path = new Path("html");
				URL fileURL = FileLocator.find(bundle, path, null);
				try {
					URL fileURL2 = FileLocator.toFileURL(fileURL);
					System.out.println("file url " + fileURL2.getPath());
					resource_handler.setResourceBase(fileURL2.getPath());
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				// Add the ResourceHandler to the server.
				HandlerList handlers = new HandlerList();
				handlers.setHandlers(new Handler[] { resource_handler, new DefaultHandler() });
				server.setHandler(handlers);
				// Start things up! By using the server.join() the server thread will join with the current thread.
				// See "http://docs.oracle.com/javase/1.5.0/docs/api/java/lang/Thread.html#join()" for more details.
				try {
					server.start();
					server.join();
				} catch (Exception e) {
					e.printStackTrace();
				}

				return Status.OK_STATUS;
			}
		};
		startServer.setSystem(true);
		startServer.schedule();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		GraphDB.instance.stopDB();
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in relative path
	 *
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
}
