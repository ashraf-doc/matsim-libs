/* *********************************************************************** *
 * project: org.matsim.*
 * VolumesAnalyzer.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;

/**
 * Counts the number of vehicles leaving a link, aggregated into time bins of a specified size.
 *
 * @author mrieser
 */
public class VolumesAnalyzer implements LinkLeaveEventHandler {

	private final int timeBinSize;
	private final int maxTime;
	private final int maxSlotIndex;
	private final Map<Id, int[]> links;

	public VolumesAnalyzer(final int timeBinSize, final int maxTime, final Network network) {
		this.timeBinSize = timeBinSize;
		this.maxTime = maxTime;
		this.maxSlotIndex = (this.maxTime/this.timeBinSize) + 1;
		this.links = new HashMap<Id, int[]>((int) (network.getLinks().size() * 1.1), 0.95f);
	}

	@Override
	public void handleEvent(final LinkLeaveEvent event) {
		int[] volumes = this.links.get(event.getLinkId());
		if (volumes == null) {
			volumes = new int[this.maxSlotIndex + 1]; // initialized to 0 by default, according to JVM specs
			this.links.put(event.getLinkId(), volumes);
		}
		int timeslot = getTimeSlotIndex(event.getTime());
		volumes[timeslot]++;
	}

	private int getTimeSlotIndex(final double time) {
		if (time > this.maxTime) {
			return this.maxSlotIndex;
		}
		return ((int)time / this.timeBinSize);
	}

	/**
	 * @param linkId
	 * @return Array containing the number of vehicles leaving the link <code>linkId</code> per time bin,
	 * 		starting with time bin 0 from 0 seconds to (timeBinSize-1)seconds.
	 */
	public int[] getVolumesForLink(final Id linkId) {
		return this.links.get(linkId);
	}

	/**
	 * @return Set of Strings containing all link ids for which counting-values are available.
	 */
	public Set<Id> getLinkIds() {
		return this.links.keySet();
	}

	@Override
	public void reset(final int iteration) {
		this.links.clear();
	}
}
