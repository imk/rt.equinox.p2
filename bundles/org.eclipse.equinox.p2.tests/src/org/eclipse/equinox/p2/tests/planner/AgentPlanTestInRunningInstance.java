/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import java.lang.reflect.Field;
import java.net.URI;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.engine.SimpleProfileRegistry;
import org.eclipse.equinox.internal.provisional.p2.director.*;
import org.eclipse.equinox.internal.provisional.p2.engine.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.p2.engine.IEngine;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class AgentPlanTestInRunningInstance extends AbstractProvisioningTest {
	private IProfile initialProfile = null;
	private Object previousSelfValue = null;

	public void setUp() throws Exception {
		super.setUp();

		initialProfile = getProfile(IProfileRegistry.SELF);
		if (initialProfile != null)
			return;

		if (System.getProperty("eclipse.p2.profile") == null) {
			SimpleProfileRegistry profileRegistry = (SimpleProfileRegistry) getProfileRegistry();
			try {
				Field selfField = SimpleProfileRegistry.class.getDeclaredField("self"); //$NON-NLS-1$
				selfField.setAccessible(true);
				previousSelfValue = selfField.get(profileRegistry);
				if (previousSelfValue == null)
					selfField.set(profileRegistry, "agent");
			} catch (Throwable t) {
				fail();
			}
		}
		createProfile("agent");
	}

	public void tearDown() throws Exception {
		if (initialProfile == null) {
			if (System.getProperty("eclipse.p2.profile") == null) {
				SimpleProfileRegistry profileRegistry = (SimpleProfileRegistry) getProfileRegistry();
				try {
					Field selfField = SimpleProfileRegistry.class.getDeclaredField("self"); //$NON-NLS-1$
					selfField.setAccessible(true);
					Object self = selfField.get(profileRegistry);
					if (self.equals("agent"))
						selfField.set(profileRegistry, previousSelfValue);
				} catch (Throwable t) {
					// ignore as we still want to continue tidying up
				}
			}
		} else {
			//After the test we clean up the profile
			IProfile profileAfterTestRun = getProfile(IProfileRegistry.SELF);
			IProvisioningPlan rollbackPlan = createPlanner().getDiffPlan(profileAfterTestRun, initialProfile, new NullProgressMonitor());
			assertOK("rollback plan", rollbackPlan.getStatus());
			assertOK("rollback execution", PlanExecutionHelper.executePlan(rollbackPlan, createEngine(), new ProvisioningContext(), new NullProgressMonitor()));
		}
		super.tearDown();
	}

	public void testGetAgentPlanActionNeededButUnavailable() {
		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);

		IProfile profile = getProfile(IProfileRegistry.SELF);
		IPlanner planner = createPlanner();

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {a});
		ProvisioningContext context = new ProvisioningContext(new URI[0]);

		IProvisioningPlan plan = planner.getProvisioningPlan(request, context, new NullProgressMonitor());
		assertNotOK(plan.getStatus());
	}

	public void testGetAgentPlanActionNeeded() {
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);
		createTestMetdataRepository(new IInstallableUnit[] {a, act1});

		IProfile profile = getProfile(IProfileRegistry.SELF);
		IPlanner planner = createPlanner();
		ProvisioningContext ctx = new ProvisioningContext();

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertTrue(plan.getStatus().isOK());
		assertNotNull(plan.getInstallerPlan());
	}

	public void testConflictBetweenActionAndThingBeingInstalled() {
		//This tests the case where the action is in conflict with the thing being installed
		//The action needs another version of A which is singleton
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "A", new VersionRange("[2.0.0, 2.0.0]"), null), new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);
		IInstallableUnit a2 = createIU("A", Version.createOSGi(2, 0, 0));
		createTestMetdataRepository(new IInstallableUnit[] {a, a2, act1});

		IProfile profile = getProfile(IProfileRegistry.SELF);
		IPlanner planner = createPlanner();
		ProvisioningContext ctx = new ProvisioningContext();

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertNotOK(plan.getStatus());
	}

	public void testSubsequentInstall() {
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);
		IInstallableUnit b = createEclipseIU("B");

		createTestMetdataRepository(new IInstallableUnit[] {a, act1, b});

		IPlanner planner = createPlanner();
		IEngine engine = createEngine();
		ProvisioningContext ctx = new ProvisioningContext();

		ProfileChangeRequest request = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertOK("install actions", engine.perform(plan.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1});
		assertOK("install A", engine.perform(plan, null));
		assertProfileContainsAll("Checking profile after initial install", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {a, act1});
		assertEquals(getProfile(IProfileRegistry.SELF).getProfileId(), plan.getInstallerPlan().getProfile().getProfileId());

		ProfileChangeRequest request2 = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request2.addInstallableUnits(new IInstallableUnit[] {b});
		IProvisioningPlan plan2 = planner.getProvisioningPlan(request2, ctx, new NullProgressMonitor());
		assertNull(plan2.getInstallerPlan());
		assertOK("install b", engine.perform(plan2, null));
		assertProfileContainsAll("Checking profile after initial install", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {a, act1, b});

	}

	public void testWithOveralInDependency() {
		IInstallableUnit common = createEclipseIU("Common");
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "Common", null), new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "Common", null), metaReq);

		createTestMetdataRepository(new IInstallableUnit[] {a, act1, common});

		IProfile profile = getProfile(IProfileRegistry.SELF);
		IPlanner planner = createPlanner();
		IEngine engine = createEngine();
		ProvisioningContext ctx = new ProvisioningContext();

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertOK("install actions", engine.perform(plan.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1, common});
		assertOK("install A", engine.perform(plan, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {a, common, act1});
	}

	public void testTwoInstallWithActions() {
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);

		IProvidedCapability act2Cap = MetadataFactory.createProvidedCapability("p2.action", "action2", DEFAULT_VERSION);
		IInstallableUnit act2 = createIU("Action2", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act2Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq2 = createRequiredCapabilities("p2.action", "action2", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit b = createIUWithMetaRequirement("B", DEFAULT_VERSION, true, NO_REQUIRES, metaReq2);

		createTestMetdataRepository(new IInstallableUnit[] {a, b, act1, act2,});

		IPlanner planner = createPlanner();
		IEngine engine = createEngine();

		ProvisioningContext ctx = new ProvisioningContext();

		//install A which will install Action1
		ProfileChangeRequest request = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertOK("install actions", engine.perform(plan.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1});
		assertOK("install A", engine.perform(plan, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {a, act1});

		//install B which will install Action2
		ProfileChangeRequest request2 = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request2.addInstallableUnits(new IInstallableUnit[] {b});
		IProvisioningPlan plan2 = planner.getProvisioningPlan(request2, ctx, new NullProgressMonitor());
		assertOK("install actions", engine.perform(plan2.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act2, act1, a});
		assertOK("install A", engine.perform(plan2, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act2, act1, b, a});
	}

	public void testCompleteScenario() {
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);

		IProvidedCapability act1bCap = MetadataFactory.createProvidedCapability("p2.action", "action1b", DEFAULT_VERSION);
		IInstallableUnit act1b = createIU("Action1b", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1bCap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReqb = createRequiredCapabilities("p2.action", "action1b", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a111 = createIUWithMetaRequirement("A", Version.createOSGi(1, 1, 1), true, NO_REQUIRES, metaReqb);

		IProvidedCapability act2Cap = MetadataFactory.createProvidedCapability("p2.action", "action2", DEFAULT_VERSION);
		IInstallableUnit act2 = createIU("Action2", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act2Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq2 = createRequiredCapabilities("p2.action", "action2", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit b = createIUWithMetaRequirement("B", DEFAULT_VERSION, true, NO_REQUIRES, metaReq2);

		IInstallableUnit c = createEclipseIU("C");
		createTestMetdataRepository(new IInstallableUnit[] {a, b, act1, act2, c, act1b, a111});

		IPlanner planner = createPlanner();
		IEngine engine = createEngine();
		ProvisioningContext ctx = new ProvisioningContext();

		//install A which will install Action1
		ProfileChangeRequest request = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request.addInstallableUnits(new IInstallableUnit[] {a});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertOK("install actions for A", engine.perform(plan.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1});
		assertOK("install A", engine.perform(plan, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {a, act1});

		//install B which will install Action2
		ProfileChangeRequest request2 = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request2.addInstallableUnits(new IInstallableUnit[] {b});
		IProvisioningPlan plan2 = planner.getProvisioningPlan(request2, ctx, new NullProgressMonitor());
		assertOK("install actions for B", engine.perform(plan2.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act2, act1, a});
		assertOK("install B", engine.perform(plan2, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act2, act1, b, a});

		//install C
		ProfileChangeRequest requestForC = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		requestForC.addInstallableUnits(new IInstallableUnit[] {c});
		IProvisioningPlan planForC = planner.getProvisioningPlan(requestForC, ctx, new NullProgressMonitor());
		assertNull(planForC.getInstallerPlan());
		assertOK("install C", engine.perform(planForC, null));
		assertProfileContainsAll("Checking profile after C", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1, act2, a, b, c});
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act2, act1, b, a});

		//update A which will install Action1b
		ProfileChangeRequest requestUpdateA = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		requestUpdateA.removeInstallableUnits(new IInstallableUnit[] {a});
		requestUpdateA.addInstallableUnits(new IInstallableUnit[] {a111});
		IProvisioningPlan planUpdateA = planner.getProvisioningPlan(requestUpdateA, ctx, new NullProgressMonitor());
		assertOK("Checking planUpdateA", planUpdateA.getStatus());
		assertOK("install actions for A 1.1.1", engine.perform(planUpdateA.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1, act1b});
		assertOK("install A", engine.perform(planUpdateA, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1b, a111, b, c});
		assertEquals(0, getProfile(IProfileRegistry.SELF).query(new InstallableUnitQuery("Action1", DEFAULT_VERSION), new Collector(), null).size());

		//uninstall A
		ProfileChangeRequest request3 = new ProfileChangeRequest(getProfile(IProfileRegistry.SELF));
		request3.removeInstallableUnits(new IInstallableUnit[] {a111});
		IProvisioningPlan plan3 = planner.getProvisioningPlan(request3, ctx, new NullProgressMonitor());
		//		assertNull(plan3.getInstallerPlan());	//TODO
		assertOK("install actions", engine.perform(plan3.getInstallerPlan(), null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {act1b}); //At this point there is not 
		assertOK("install A", engine.perform(plan3, null));
		assertProfileContainsAll("Checking profile after install of actions", getProfile(IProfileRegistry.SELF), new IInstallableUnit[] {c, b, act2});
	}

	public void testConflictBetweenActions() {
		IProvidedCapability act1Cap = MetadataFactory.createProvidedCapability("p2.action", "action1", DEFAULT_VERSION);
		IInstallableUnit act1 = createIU("Action1", DEFAULT_VERSION, null, NO_REQUIRES, new IProvidedCapability[] {act1Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReq = createRequiredCapabilities("p2.action", "action1", new VersionRange("[0.0.0, 1.0.0]"), null);
		IInstallableUnit a = createIUWithMetaRequirement("A", DEFAULT_VERSION, true, NO_REQUIRES, metaReq);

		IProvidedCapability act1v2Cap = MetadataFactory.createProvidedCapability("p2.action", "action2", Version.createOSGi(2, 0, 0));
		IInstallableUnit act1v2 = createIU("Action1", Version.create("2.0.0"), null, NO_REQUIRES, new IProvidedCapability[] {act1v2Cap}, NO_PROPERTIES, null, NO_TP_DATA, true);

		IRequiredCapability[] metaReqd = createRequiredCapabilities("p2.action", "action2", new VersionRange("[2.0.0, 2.0.0]"), null);
		IInstallableUnit d = createIUWithMetaRequirement("D", DEFAULT_VERSION, true, NO_REQUIRES, metaReqd);

		createTestMetdataRepository(new IInstallableUnit[] {a, act1, d, act1v2});

		IProfile profile = getProfile(IProfileRegistry.SELF);
		IPlanner planner = createPlanner();
		ProvisioningContext ctx = new ProvisioningContext();

		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addInstallableUnits(new IInstallableUnit[] {a, d});
		IProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertNotOK(plan.getStatus());
	}
}
