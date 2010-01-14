/*******************************************************************************
 * Copyright (c) 2008, 2009 Code 9 and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Code 9 - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.metadata.mirror;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.internal.p2.metadata.repository.Activator;
import org.eclipse.equinox.internal.p2.metadata.repository.MetadataRepositoryManager;
import org.eclipse.equinox.internal.p2.repository.helpers.RepositoryHelper;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.repository.IRepositoryManager;

/**
 * An application that performs mirroring of artifacts between repositories.
 */
public class MirrorApplication implements IApplication {

	private String[] rootSpecs;
	private URI sourceLocation;
	private URI destinationLocation;
	private IMetadataRepository source;
	private IMetadataRepository destination;
	private boolean transitive = false;
	private boolean append = true;
	private IMetadataRepositoryManager cachedManager;
	private boolean sourceLoaded = false;
	private boolean destinationLoaded = false;
	private String destinationName;

	/**
	 * Convert a list of tokens into an array. The list separator has to be
	 * specified.
	 */
	public static String[] getArrayArgsFromString(String list, String separator) {
		if (list == null || list.trim().equals("")) //$NON-NLS-1$
			return new String[0];
		List result = new ArrayList();
		for (StringTokenizer tokens = new StringTokenizer(list, separator); tokens.hasMoreTokens();) {
			String token = tokens.nextToken().trim();
			if (!token.equals("")) { //$NON-NLS-1$
				if ((token.indexOf('[') >= 0 || token.indexOf('(') >= 0) && tokens.hasMoreTokens())
					result.add(token + separator + tokens.nextToken());
				else
					result.add(token);
			}
		}
		return (String[]) result.toArray(new String[result.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	public Object start(IApplicationContext context) throws Exception {
		try {
			initializeFromArguments((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
			setupRepositories();
			new Mirroring().mirror(source, destination, rootSpecs, transitive);
			return IApplication.EXIT_OK;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			throw e;
		} finally {
			//if the repository was not already loaded before the mirror application started, close it.
			if (!sourceLoaded && sourceLocation != null)
				getManager().removeRepository(sourceLocation);
			if (!destinationLoaded && destinationLocation != null)
				getManager().removeRepository(destinationLocation);
		}
	}

	private void setupRepositories() throws ProvisionException {
		if (destinationLocation == null || sourceLocation == null)
			throw new IllegalStateException("Must specify a source and destination"); //$NON-NLS-1$

		//Check if repositories are already loaded
		//TODO modify the contains statement once the API is available
		sourceLoaded = getManager().contains(sourceLocation);
		//TODO modify the contains statement once the API is available
		destinationLoaded = getManager().contains(destinationLocation);

		//must execute before initializeDestination is called
		source = getManager().loadRepository(sourceLocation, 0, null);
		destination = initializeDestination();
	}

	/*
	 * Return the metadata repository manager. We need to check the service here
	 * as well as creating one manually in case we are running a stand-alone application
	 * in which no one has registered a manager yet.
	 */
	private IMetadataRepositoryManager getManager() {
		if (cachedManager != null)
			return cachedManager;
		IMetadataRepositoryManager result = (IMetadataRepositoryManager) ServiceHelper.getService(Activator.getContext(), IMetadataRepositoryManager.class.getName());
		// service not available... create one and hang onto it
		if (result == null) {
			cachedManager = new MetadataRepositoryManager();
			result = cachedManager;
		}
		return result;
	}

	private IMetadataRepository initializeDestination() throws ProvisionException {
		try {
			IMetadataRepository repository = getManager().loadRepository(destinationLocation, IRepositoryManager.REPOSITORY_HINT_MODIFIABLE, null);
			if (repository != null && repository.isModifiable()) {
				if (destinationName != null)
					repository.setName(destinationName);
				if (!append)
					repository.removeAll();
				return repository;
			}
		} catch (ProvisionException e) {
			//fall through and create repo
		}
		//This code assumes source has been successfully loaded before this point
		//No existing repository; create a new repository at destinationLocation but with source's attributes.
		// TODO for now create a Simple repo by default.
		return (IMetadataRepository) RepositoryHelper.validDestinationRepository(getManager().createRepository(destinationLocation, destinationName == null ? source.getName() : destinationName, IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, source.getProperties()));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	public void stop() {
		//do nothing
	}

	public void initializeFromArguments(String[] args) throws Exception {
		if (args == null)
			return;
		for (int i = 0; i < args.length; i++) {
			// check for args with parameters. If we are at the last argument or 
			// if the next one has a '-' as the first character, then we can't have 
			// an arg with a param so continue.
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			if (args[i - 1].equalsIgnoreCase("-destinationName")) //$NON-NLS-1$
				destinationName = arg;
			if (args[i - 1].equalsIgnoreCase("-writeMode")) //$NON-NLS-1$
				if (args[i].equalsIgnoreCase("clean")) //$NON-NLS-1$
					append = false;

			try {
				if (args[i - 1].equalsIgnoreCase("-source")) //$NON-NLS-1$
					sourceLocation = RepositoryHelper.localRepoURIHelper(URIUtil.fromString(arg));
				if (args[i - 1].equalsIgnoreCase("-destination")) //$NON-NLS-1$
					destinationLocation = RepositoryHelper.localRepoURIHelper(URIUtil.fromString(arg));
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Repository location (" + arg + ") must be a URL."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (args[i - 1].equalsIgnoreCase("-roots")) //$NON-NLS-1$
				rootSpecs = getArrayArgsFromString(arg, ","); //$NON-NLS-1$
			if (args[i - 1].equalsIgnoreCase("-transitive")) //$NON-NLS-1$
				transitive = true;
		}
	}
}