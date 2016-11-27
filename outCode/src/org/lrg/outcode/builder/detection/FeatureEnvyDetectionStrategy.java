package org.lrg.outcode.builder.detection;

import org.eclipse.jdt.core.IJavaElement;

public class FeatureEnvyDetectionStrategy  implements IDetectionStrategy{

	@Override
	public IJavaElement detect(IJavaElement element) {
		return element;
	}

}
