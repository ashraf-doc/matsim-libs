/* *********************************************************************** *
 * project: org.matsim.*
 * PlanToPlanStepBasedOnEvents.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.cadyts.measurement;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.PlansTranslator;

import cadyts.demand.PlanBuilder;
import org.matsim.core.config.ConfigUtils;

import javax.inject.Inject;

//public class PlanToPlanStepBasedOnEvents implements PlansTranslator<Link>, LinkLeaveEventHandler, 
//public class DistributionPlanToPlanStepBasedOnEvents implements PlansTranslator<Integer>, LinkLeaveEventHandler, 
public class MeasurementPlanToPlanStepBasedOnEvents implements PlansTranslator<Measurement>, LinkLeaveEventHandler, 
		PersonDepartureEventHandler, PersonArrivalEventHandler {
	
	private static final Logger log = Logger.getLogger(MeasurementPlanToPlanStepBasedOnEvents.class);

	private final Scenario scenario;

	private final Set<Id> driverAgents;
	
	private int iteration = -1;

	// this is _only_ there for output:
	Set<Plan> plansEverSeen = new HashSet<>();

	private static final String STR_PLANSTEPFACTORY = "planStepFactory";
	private static final String STR_ITERATION = "iteration";

	private final Set<Id<Measurement>> calibratedLinks = new HashSet<>() ;

	@Inject
	MeasurementPlanToPlanStepBasedOnEvents(final Scenario scenario) {
		this.scenario = scenario;
		Set<String> abc = ConfigUtils.addOrGetModule(scenario.getConfig(), CadytsConfigGroup.GROUP_NAME, CadytsConfigGroup.class).getCalibratedItems();
		for ( String str : abc ) {
			this.calibratedLinks.add( Id.create( str, Measurement.class ) ) ;
		}
		this.driverAgents = new HashSet<>();
	}

	private long plansFound = 0;
	private long plansNotFound = 0;

	@Override
//	public final cadyts.demand.Plan<Link> getPlanSteps(final Plan plan) {
//	public final cadyts.demand.Plan<Integer> getPlanSteps(final Plan plan) {
	public final cadyts.demand.Plan<Measurement> getPlanSteps(final Plan plan) {
//		PlanBuilder<Link> planStepFactory = (PlanBuilder<Link>) plan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
//		PlanBuilder<Integer> planStepFactory = (PlanBuilder<Integer>) plan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
		PlanBuilder<Measurement> planStepFactory = (PlanBuilder<Measurement>) plan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
		if (planStepFactory == null) {
			this.plansNotFound++;
			return null;
		}
		this.plansFound++;
//		final cadyts.demand.Plan<Link> planSteps = planStepFactory.getResult();
//		final cadyts.demand.Plan<Integer> planSteps = planStepFactory.getResult();
		final cadyts.demand.Plan<Measurement> planSteps = planStepFactory.getResult();
		return planSteps;
	}

	@Override
	public void reset(final int iteration) {
		this.iteration = iteration;

		log.warn("found " + this.plansFound + " out of " + (this.plansFound + this.plansNotFound) + " ("
				+ (100. * this.plansFound / (this.plansFound + this.plansNotFound)) + "%)");
		log.warn("(above values may both be at zero for a couple of iterations if multiple plans per agent all have no score)");

		this.driverAgents.clear();
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (event.getLegMode().equals(TransportMode.car)) this.driverAgents.add(event.getPersonId());
	}
	
	@Override
	public void handleEvent(PersonArrivalEvent event) {
		this.driverAgents.remove(event.getPersonId());
	}
	
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		
		// if it is not a driver, ignore the event
		if (!driverAgents.contains(event.getPersonId())) return;
		
		// if only a subset of links is calibrated but the link is not contained, ignore the event
		if (!calibratedLinks.contains(event.getLinkId())) return;
		
		// get the "Person" behind the id:
		Person person = this.scenario.getPopulation().getPersons().get(event.getPersonId());
		
		// get the selected plan:
		Plan selectedPlan = person.getSelectedPlan();
		
		// get the planStepFactory for the plan (or create one):
//		PlanBuilder<Link> tmpPlanStepFactory = getPlanStepFactoryForPlan(selectedPlan);
//		PlanBuilder<Double> tmpPlanStepFactory = getPlanStepFactoryForPlan(selectedPlan);
		PlanBuilder<Measurement> tmpPlanStepFactory = getPlanStepFactoryForPlan(selectedPlan);
		
		// TODO
//		if (tmpPlanStepFactory != null) {
//						
//			Link link = this.scenario.getNetwork().getLinks().get(event.getLinkId());
//					
//			// add the "turn" to the planStepfactory
//			tmpPlanStepFactory.addTurn(link, (int) event.getTime());
//		}
	}

	// ###################################################################################
	// only private functions below here (low level functionality)

//	private PlanBuilder<Link> getPlanStepFactoryForPlan(final Plan selectedPlan) {
//	private PlanBuilder<Double> getPlanStepFactoryForPlan(final Plan selectedPlan) {
	private PlanBuilder<Measurement> getPlanStepFactoryForPlan(final Plan selectedPlan) {
//		PlanBuilder<Link> planStepFactory = null;
//		PlanBuilder<Double> planStepFactory = null;
		PlanBuilder<Measurement> planStepFactory = null;

//		planStepFactory = (PlanBuilder<Link>) selectedPlan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
//		planStepFactory = (PlanBuilder<Double>) selectedPlan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
		planStepFactory = (PlanBuilder<Measurement>) selectedPlan.getCustomAttributes().get(STR_PLANSTEPFACTORY);
		Integer factoryIteration = (Integer) selectedPlan.getCustomAttributes().get(STR_ITERATION);
		if (planStepFactory == null || factoryIteration == null || factoryIteration != this.iteration) {
			// attach the iteration number to the plan:
			selectedPlan.getCustomAttributes().put(STR_ITERATION, this.iteration);

			// construct a new PlanBulder and attach it to the plan:
//			planStepFactory = new PlanBuilder<Link>();
//			planStepFactory = new PlanBuilder<Double>();
			planStepFactory = new PlanBuilder<Measurement>();
			selectedPlan.getCustomAttributes().put(STR_PLANSTEPFACTORY, planStepFactory);

			// memorize the plan as being seen:
			this.plansEverSeen.add(selectedPlan);
		}

		return planStepFactory;
	}

}
