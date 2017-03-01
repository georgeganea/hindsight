package org.lrg.hindsight.classes.version;

import org.lrg.outcode.IHindsight;

import com.salexandru.xcore.utils.interfaces.IPropertyComputer;
import com.salexandru.xcore.utils.metaAnnotation.PropertyComputer;

import outcode.metamodel.entity.XClassVersion;

@PropertyComputer
public class ToString implements IPropertyComputer<String, XClassVersion>{

	@Override
	public String compute(XClassVersion entity) {
		return entity.getUnderlyingObject().getProperty(IHindsight.COMMIT).toString();
	}

}
