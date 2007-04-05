/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.simpleconfigurator.internal;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.equinox.configurator.Configurator;
import org.eclipse.equinox.internal.simpleconfigurator.console.ConfiguratorCommandProvider;
import org.eclipse.equinox.internal.simpleconfigurator.utils.SimpleConfiguratorConstants;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.*;

/**
 * At its start, SimpleConfigurator bundle does the followings.
 * 
 * 1. A value will be gotten by @{link BundleContext#getProperty(key)} with 
 * {@link SimpleConfiguratorConstants#PROP_KEY_CONFIGURL} as a key.
 * The value will be used for the referal URL. Under the url, there must be a simple 
 * bundles list file to be installed with thier start level and flag of marked as started.
 * 
 * 2. If the value is null, do nothing any more.
 * 3. Otherwise, retrieve the bundles list from the url and install,
 *  set start level of and start bundles, as specified.
 * 
 * 4. A value will be gotten by @{link BundleContext#getProperty(key)} with 
 * {@link SimpleConfiguratorConstants#PROP_KEY_EXCLUSIVE_INSTALLATION} as a key.
 * 
 * 5. If it equals "false", it will do exclusive installation, which means that 
 * the bundles will not be listed in the specified url but installed at the time
 * of the method call except SystemBundle will be uninstalled. 
 * Otherwise, no uninstallation will not be done.
 * 
 */
public class Activator implements BundleActivator {
	final static boolean DEBUG = false;
	private BundleContext context;
	private SimpleConfiguratorImpl bundleConfigurator;
	private ServiceRegistration configuratorRegistration;
	private ServiceRegistration commandRegistration;

	/**
	 * @return URL
	 */
	private URL getConfigUrl() {
		try {
			String specifiedURL = context.getProperty(SimpleConfiguratorConstants.PROP_KEY_CONFIGURL);
			if (specifiedURL != null)
				return new URL(specifiedURL);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void start(BundleContext context) throws Exception {
		this.context = context;
		bundleConfigurator = new SimpleConfiguratorImpl(context);
		URL configUrl = getConfigUrl();
		if (DEBUG)
			System.out.println("configUrl=" + configUrl);
		if (configUrl != null)
			bundleConfigurator.applyConfiguration(configUrl);		
		
		Dictionary props = new Hashtable();
		props.put(Constants.SERVICE_VENDOR, "Eclipse");
		props.put(Constants.SERVICE_PID, SimpleConfiguratorConstants.TARGET_CONFIGURATOR_NAME);
		configuratorRegistration = context.registerService(Configurator.class.getName(), bundleConfigurator, props);

		commandRegistration = context.registerService(CommandProvider.class.getName(), new ConfiguratorCommandProvider(context), null);
		
		if (DEBUG)
			System.out.println("registered Configurator");
	}

	public void stop(BundleContext context) throws Exception {
		if (configuratorRegistration != null) {
			configuratorRegistration.unregister();
			configuratorRegistration = null;
		}
		if (commandRegistration != null) {
			commandRegistration.unregister();
			commandRegistration = null;
		}
		this.bundleConfigurator = null;
		this.context = null;
	}
}
