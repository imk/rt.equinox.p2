/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.ui.viewers;

import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.ui.IUPropertyUtils;
import org.eclipse.equinox.internal.provisional.p2.ui.ProvUI;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

public class IUComparator extends ViewerComparator {
	public static final int IU_NAME = 0;
	public static final int IU_ID = 1;
	private int key;
	private boolean showingId = false;

	public IUComparator(int sortKey) {
		this.key = sortKey;
		showingId = sortKey == IU_ID;
	}

	/**
	 * Use the specified column config to determine
	 * whether the id should be used in lieu of an empty name
	 * when sorting.
	 * 
	 * @param columnConfig
	 */
	public void useColumnConfig(IUColumnConfig[] columnConfig) {
		for (int i = 0; i < columnConfig.length; i++)
			if (columnConfig[i].columnField == IUColumnConfig.COLUMN_ID) {
				showingId = true;
				break;
			}
	}

	public int compare(Viewer viewer, Object obj1, Object obj2) {
		IInstallableUnit iu1 = (IInstallableUnit) ProvUI.getAdapter(obj1, IInstallableUnit.class);
		IInstallableUnit iu2 = (IInstallableUnit) ProvUI.getAdapter(obj2, IInstallableUnit.class);
		if (iu1 == null || iu2 == null)
			// If these are not iu's use the super class comparator.
			return super.compare(viewer, obj1, obj2);

		String key1, key2;
		if (key == IU_NAME) {
			// Compare the iu names in the default locale.
			// If a name is not defined, we use blank if we know the id is shown in another
			// column.  If the id is not shown elsewhere, then we are displaying it, so use
			// the id instead.
			key1 = IUPropertyUtils.getIUProperty(iu1, IInstallableUnit.PROP_NAME);
			if (key1 == null)
				if (showingId)
					key1 = ""; //$NON-NLS-1$
				else
					key1 = iu1.getId();
			key2 = IUPropertyUtils.getIUProperty(iu2, IInstallableUnit.PROP_NAME);
			if (key2 == null)
				if (showingId)
					key2 = ""; //$NON-NLS-1$
				else
					key2 = iu2.getId();
		} else {
			key1 = iu1.getId();
			key2 = iu2.getId();
		}

		int result = 0;
		result = key1.compareToIgnoreCase(key2);
		if (result == 0) {
			// We want to show later versions first so compare backwards.
			result = iu2.getVersion().compareTo(iu1.getVersion());
		}
		return result;
	}

}
