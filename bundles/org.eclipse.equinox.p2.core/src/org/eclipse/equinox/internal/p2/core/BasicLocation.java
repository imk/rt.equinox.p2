/*******************************************************************************
 * Copyright (c) 2004, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.core;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.equinox.internal.provisional.p2.core.location.AgentLocation;
import org.eclipse.osgi.service.datalocation.Location;

/**
 * Internal class.
 */
public class BasicLocation implements AgentLocation {
	private static class MockLocker implements Locker {
		public boolean lock() {
			// locking always successful
			return true;
		}

		public void release() {
			// nothing to release
		}
	}

	private boolean isReadOnly;
	private URL location = null;
	private Location parent;
	private URL defaultValue;

	// locking related fields
	private File lockFile;
	private Locker locker;
	public static final String PROP_OSGI_LOCKING = "osgi.locking"; //$NON-NLS-1$
	public static boolean DEBUG;

	private static boolean isRunningWithNio() {
		try {
			Class.forName("java.nio.channels.FileLock"); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			return false;
		}
		return true;
	}

	public static Locker createLocker(File lock, String lockMode) {
		if (lockMode == null)
			lockMode = Activator.context.getProperty(PROP_OSGI_LOCKING);

		if ("none".equals(lockMode)) //$NON-NLS-1$
			return new MockLocker();

		if ("java.io".equals(lockMode)) //$NON-NLS-1$
			return new Locker_JavaIo(lock);

		if ("java.nio".equals(lockMode)) { //$NON-NLS-1$
			if (isRunningWithNio())
				return new Locker_JavaNio(lock);
			else
				// TODO should we return null here.  NIO was requested but we could not do it...
				return new Locker_JavaIo(lock);
		}

		//	Backup case if an invalid value has been specified
		if (isRunningWithNio())
			return new Locker_JavaNio(lock);
		else
			return new Locker_JavaIo(lock);
	}

	public BasicLocation(String property, URL defaultValue, boolean isReadOnly) {
		super();
		this.defaultValue = defaultValue;
		this.isReadOnly = isReadOnly;
	}

	public boolean allowsDefault() {
		return defaultValue != null;
	}

	public URL getDefault() {
		return defaultValue;
	}

	public synchronized Location getParentLocation() {
		return parent;
	}

	public synchronized URL getURL() {
		if (location == null && defaultValue != null)
			setURL(defaultValue, false);
		return location;
	}

	public synchronized boolean isSet() {
		return location != null;
	}

	public boolean isReadOnly() {
		return isReadOnly;
	}

	/**
	 * @deprecated
	 */
	public synchronized boolean setURL(URL value, boolean lock) {
		//		if (location != null)
		//			throw new IllegalStateException(Messages.ECLIPSE_CANNOT_CHANGE_LOCATION);
		////		File file = null;
		////		if (value.getProtocol().equalsIgnoreCase("file")) { //$NON-NLS-1$
		////			try {
		////				String basePath = new File(value.getFile()).getCanonicalPath();
		////				value = new URL("file:" + basePath); //$NON-NLS-1$
		////			} catch (IOException e) {
		////				// do nothing just use the original value
		////			}
		////			file = new File(value.getFile(), LOCK_FILENAME);
		////		}
		//		lock = lock && !isReadOnly;
		//		if (lock) {
		//			try {
		//				if (!lock(file))
		//					return false;
		//			} catch (IOException e) {
		//				return false;
		//			}
		//		}
		//		lockFile = file;
		location = value;
		//		LocationManager.buildURL(value.toExternalForm(), true);
		//		if (property != null)
		//			System.setProperty(property, location.toExternalForm());
		return lock;
	}

	public boolean set(URL value, boolean lock) {
		location = value;
		return lock;
	}

	public synchronized void setParent(Location value) {
		parent = value;
	}

	public synchronized boolean lock() throws IOException {
		if (!isSet())
			return false;
		return lock(lockFile);
	}

	private boolean lock(File lock) throws IOException {
		if (lock == null || isReadOnly)
			return false;

		File parentFile = new File(lock.getParent());
		if (!parentFile.exists())
			if (!parentFile.mkdirs())
				return false;

		setLocker(lock);
		if (locker == null)
			return true;
		boolean locked = false;
		try {
			locked = locker.lock();
			return locked;
		} finally {
			if (!locked)
				locker = null;
		}
	}

	private void setLocker(File lock) {
		if (locker != null)
			return;
		String lockMode = Activator.context.getProperty(PROP_OSGI_LOCKING);
		locker = createLocker(lock, lockMode);
	}

	public synchronized void release() {
		if (locker != null)
			locker.release();
	}

	public URL getArtifactRepositoryURL() {
		//the cache is a co-located repository
		return getMetadataRepositoryURL();
	}

	public URL getMetadataRepositoryURL() {
		try {
			return new URL(getDataArea(Activator.ID), "cache/"); //$NON-NLS-1$
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}

	public URL getDataArea(String touchpointId) {
		try {
			return new URL(getURL(), touchpointId + '/');
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}
}