package org.lrg.outcode.builder.detection;

import org.eclipse.jdt.core.IJavaElement;

public interface IDetectionStrategy{

	public IJavaElement detect(IJavaElement element);
}
