package org.lrg.outcode;

import org.eclipse.jdt.core.IType;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.lrg.outcode.activator.GraphDB;
import org.osgi.framework.BundleContext;

import com.salexandru.xcore.utils.interfaces.XEntity;

import ro.lrg.insider.view.ToolRegistration;
import ro.lrg.insider.view.ToolRegistration.XEntityConverter;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.lrg.hindsight"; //$NON-NLS-1$

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

		plugin = this;

		ToolRegistration.getInstance().registerXEntityConverter(new XEntityConverter() {

			@Override
			public XEntity convert(Object element) {
				if (element instanceof IType) {
					return outcode.metamodel.factory.Factory.getInstance().createXClass((IType) element);
				}
				if (element instanceof org.neo4j.graphdb.Node) {
					return outcode.metamodel.factory.Factory.getInstance().createXClassVersion((org.neo4j.graphdb.Node) element);
				}
				return null;
			}

		});
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
