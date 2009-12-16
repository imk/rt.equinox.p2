/*******************************************************************************
 *  Copyright (c) 2008 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.engine;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Query;

public interface ISurrogateProfileHandler {

	public abstract IProfile createProfile(String id);

	public abstract boolean isSurrogate(IProfile profile);

	public abstract Collector queryProfile(IProfile profile, Query query, Collector collector, IProgressMonitor monitor);

	public abstract boolean updateProfile(IProfile selfProfile);

}