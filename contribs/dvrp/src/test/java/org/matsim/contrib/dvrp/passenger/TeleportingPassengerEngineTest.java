/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.dvrp.passenger;

import static org.matsim.contrib.dvrp.passenger.PassengerEngineTestFixture.MODE;
import static org.matsim.contrib.dvrp.passenger.PassengerEngineTestFixture.PERSON_ID;

import java.util.Set;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerEngineQSimModule.PassengerEngineType;
import org.matsim.contrib.dvrp.passenger.TeleportingPassengerEngine.TeleportedRouteCalculator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.contrib.dvrp.run.MobsimTimerProvider;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.population.routes.GenericRouteImpl;

/**
 * @author Michal Maciejewski (michalm)
 */
public class TeleportingPassengerEngineTest {
	private final PassengerEngineTestFixture fixture = new PassengerEngineTestFixture();

	@Test
	public void test_teleported() {
		fixture.addPersonWithLeg(fixture.linkAB, fixture.linkBA, 0);

		TeleportedRouteCalculator teleportedRouteCalculator = request -> {
			Route route = new GenericRouteImpl(request.getFromLink().getId(), request.getToLink().getId());
			route.setTravelTime(999);
			route.setDistance(555);
			return route;
		};
		PassengerRequestValidator requestValidator = request -> Set.of();//valid
		createQSim(teleportedRouteCalculator, requestValidator).run();

		var requestId = Id.create("taxi_0", Request.class);
		fixture.assertPassengerEvents(new ActivityEndEvent(0, PERSON_ID, fixture.linkAB.getId(), null, "start"),
				new PersonDepartureEvent(0, PERSON_ID, fixture.linkAB.getId(), MODE),
				new PassengerRequestScheduledEvent(0, MODE, requestId, PERSON_ID, null, 0, 999),
				new PassengerPickedUpEvent(0, MODE, requestId, PERSON_ID, null),
				new PassengerDroppedOffEvent(999, MODE, requestId, PERSON_ID, null),
				new TeleportationArrivalEvent(999, PERSON_ID, 555, MODE),
				new PersonArrivalEvent(999, PERSON_ID, fixture.linkBA.getId(), MODE),
				new ActivityStartEvent(999, PERSON_ID, fixture.linkBA.getId(), null, "end"));
	}

	@Test
	public void test_rejected() {
		fixture.addPersonWithLeg(fixture.linkAB, fixture.linkBA, 0);

		TeleportedRouteCalculator teleportedRouteCalculator = request -> null; // unused
		PassengerRequestValidator requestValidator = request -> Set.of("invalid");
		createQSim(teleportedRouteCalculator, requestValidator).run();

		var requestId = Id.create("taxi_0", Request.class);
		fixture.assertPassengerEvents(new ActivityEndEvent(0, PERSON_ID, fixture.linkAB.getId(), null, "start"),
				new PersonDepartureEvent(0, PERSON_ID, fixture.linkAB.getId(), MODE),
				new PassengerRequestRejectedEvent(0, MODE, requestId, PERSON_ID, "invalid"),
				new PersonStuckEvent(0, PERSON_ID, fixture.linkAB.getId(), MODE));
	}

	private QSim createQSim(TeleportedRouteCalculator teleportedRouteCalculator,
			PassengerRequestValidator requestValidator) {
		return new QSimBuilder(fixture.config).useDefaults()
				.addQSimModule(new PassengerEngineQSimModule(MODE, PassengerEngineType.TELEPORTING))
				.addQSimModule(new AbstractDvrpModeQSimModule(MODE) {
					@Override
					protected void configureQSim() {
						bind(MobsimTimer.class).toProvider(MobsimTimerProvider.class).asEagerSingleton();
						bindModal(PassengerRequestCreator.class).toInstance(fixture.requestCreator);
						bindModal(TeleportedRouteCalculator.class).toInstance(teleportedRouteCalculator);
						bindModal(PassengerRequestValidator.class).toInstance(requestValidator);
						bindModal(Network.class).toInstance(fixture.network);
					}
				})
				.configureQSimComponents(components -> components.addComponent(DvrpModes.mode(MODE)))
				.build(fixture.scenario, fixture.eventsManager);
	}
}
