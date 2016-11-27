package org.lrg.outcode.views;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.lrg.outcode.Activator;
import org.lrg.outcode.views.browseractions.GetDBStatus;
import org.lrg.outcode.views.browseractions.IBrowserAction;
import org.lrg.outcode.views.browseractions.OpenCommitDiff;

public class Hindsight extends ViewPart {

	public static final String VIEW_ID = "outCode.browser";
	private Browser browser;
	private BrowserFunction function;
	private Map<String, IBrowserAction> configuredActions = new HashMap<>();

	@Override
	public void createPartControl(Composite parent) {
		browser = new Browser(parent, SWT.NONE);
		function = new CustomFunction(browser, "theJavaFunction");
		URL indexPage = Platform.getBundle(Activator.PLUGIN_ID).getResource("html/src/client/index.html");
		try {
			URL resolvedFileURL = FileLocator.toFileURL(indexPage);
			browser.setUrl(resolvedFileURL.toExternalForm());
		} catch (IOException e) {
			e.printStackTrace();
		}

		configuredActions.put(OpenCommitDiff.class.getSimpleName(), new OpenCommitDiff());
		configuredActions.put(GetDBStatus.class.getSimpleName(), new GetDBStatus());
	}

	public void sendMessage(String message) {
		browser.evaluate("document.dispatchEvent(new CustomEvent('build', { 'detail': " + message + " }));");
	}

	class CustomFunction extends BrowserFunction {

		CustomFunction(Browser browser, String name) {
			super(browser, name);
		}

		@Override
		public Object function(Object[] arguments) {
			System.out.println("theJavaFunction() called from javascript " + arguments[0]);
			if (configuredActions.containsKey(arguments[0].toString()))
				return configuredActions.get(arguments[0].toString()).onMessage(arguments[0].toString(), arguments);
			return "no action configured for method";

			// Object returnValue = new Object[] { new Short((short) 3), new Boolean(true), null, new Object[] { "a string", new Boolean(false) }, "hi", new Float(2.0f / 3.0f), };
			// int z = 3 / 0; // uncomment to cause a java error instead
			// return returnValue;
		}
	}

	@Override
	public void setFocus() {
		browser.setFocus();
	}

	@Override
	public void dispose() {
		function.dispose();
		browser.dispose();
		super.dispose();
	}
}
