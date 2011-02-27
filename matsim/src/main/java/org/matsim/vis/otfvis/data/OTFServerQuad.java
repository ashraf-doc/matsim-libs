/* *********************************************************************** *
 * project: org.matsim.*
 * OTFServerQuad.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package org.matsim.vis.otfvis.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.vis.otfvis.interfaces.OTFDataReader;
import org.matsim.vis.otfvis.interfaces.OTFServerRemote;
import org.matsim.vis.snapshots.writers.VisLink;
import org.matsim.vis.snapshots.writers.VisNetwork;
import org.matsim.vis.snapshots.writers.VisNode;



/**
 * OTFServerQuad is the quad representation of all elements of the network on the server
 * side. This QuadTree is mirrored on the client side by OTFClientQuad.
 *
 * @author dstrippgen
 * @deprecated use OTFServerQuad2 this class is only in the code for correct
 * deserialization.
 */
@Deprecated
public class OTFServerQuad extends QuadTree<OTFDataWriter> implements OTFServerQuadI {

	private static final Logger log = Logger.getLogger(OTFServerQuad.class);

	private final List<OTFDataWriter> additionalElements= new LinkedList<OTFDataWriter>();

	private static class ConvertToClientExecutor implements Executor<OTFDataWriter> {
		final OTFConnectionManager connect;
		final OTFClientQuad client;

		public ConvertToClientExecutor(OTFConnectionManager connect2, OTFClientQuad client) {
			this.connect = connect2;
			this.client = client;
		}
		@Override
		@SuppressWarnings("unchecked")
		public void execute(double x, double y, OTFDataWriter writer)  {
			Collection<Class<OTFDataReader>> readerClasses = this.connect.getReadersForWriter(writer.getClass());
			for (Class readerClass : readerClasses) {
				try {
					OTFDataReader reader = (OTFDataReader)readerClass.newInstance();
					reader.setSrc(writer);
					this.client.put(x, y, reader);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
			}
		}
	}


	private static class WriteDataExecutor implements Executor<OTFDataWriter> {
		final ByteBuffer out;
		boolean writeConst;

		public WriteDataExecutor(ByteBuffer out, boolean writeConst) {
			this.out = out;
			this.writeConst = writeConst;
		}
		@Override
		public void execute(double xx, double yy, OTFDataWriter writer)  {
			try {
				if (this.writeConst) writer.writeConstData(this.out);
				else writer.writeDynData(this.out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static class ReplaceSourceExecutor implements Executor<OTFDataWriter> {
		public final VisNetwork q;

		public ReplaceSourceExecutor(VisNetwork newNet) {
			q = newNet;
		}

		@Override
		public void execute(double x, double y, OTFDataWriter writer)  {
			Object src = writer.getSrc();
			if(src instanceof VisLink) {
				VisLink link = q.getVisLinks().get(((VisLink) src).getLink().getId());
				writer.setSrc(link);
//			} else if(src instanceof QueueNode) {
//
			}
		}
	}

	private static final long serialVersionUID = 1L;
	protected double minEasting;
	protected double maxEasting;
	protected double minNorthing;
	protected double maxNorthing;
	transient private VisNetwork net;

	// Change this, find better way to transport this info into Writers
	public static double offsetEast;
	public static double offsetNorth;

	public OTFServerQuad(VisNetwork net) {
		super(0,0,0,0);
		updateBoundingBox(net);
		// has to be done later, as we do not know the writers yet!
		// fillQuadTree(net);
	}

	public OTFServerQuad(double minX, double minY, double maxX, double maxY) {
		super(minX, minY, maxX, maxY);
		// make sure, the bounding box is bigger than the biggest element, otherwise
		// requests with null == use biggest bounding box will fail on the leftmost elements
		this.minEasting = minX;
		this.maxEasting = maxX+1;
		this.minNorthing = minY;
		this.maxNorthing = maxY+1;
	}


	/**
	 * Sets a new top node in case the extremities from the c'tor are not
	 * good anymore, it also clear the QuadTree
	 * @param minX The smallest x coordinate expected
	 * @param minY The smallest y coordinate expected
	 * @param maxX The largest x coordinate expected
	 * @param maxY The largest y coordinate expected
	 */
	private void setTopNode(final double minX, final double minY, final double maxX, final double maxY) {
		this.top = new Node<OTFDataWriter>(minX, minY, maxX, maxY);
	}


	public void updateBoundingBox(VisNetwork net){
		this.minEasting = Double.POSITIVE_INFINITY;
		this.maxEasting = Double.NEGATIVE_INFINITY;
		this.minNorthing = Double.POSITIVE_INFINITY;
		this.maxNorthing = Double.NEGATIVE_INFINITY;

		for (Iterator<? extends VisNode> it = net.getVisNodes().values().iterator(); it.hasNext();) {
			VisNode node = it.next();
			this.minEasting = Math.min(this.minEasting, node.getNode().getCoord().getX());
			this.maxEasting = Math.max(this.maxEasting, node.getNode().getCoord().getX());
			this.minNorthing = Math.min(this.minNorthing, node.getNode().getCoord().getY());
			this.maxNorthing = Math.max(this.maxNorthing, node.getNode().getCoord().getY());
		}
		// make sure, the bounding box is bigger than the biggest element, otherwise
		// requests with null == use biggest bounding box will fail on the leftmost elements
		this.maxEasting +=1;
		this.maxNorthing +=1;

		this.net = net;
		offsetEast = this.minEasting;
		offsetNorth = this.minNorthing;
	}

	public void fillQuadTree(final OTFConnectionManager connect) {
		final double easting = this.maxEasting - this.minEasting;
		final double northing = this.maxNorthing - this.minNorthing;
		// set top node
		setTopNode(0, 0, easting, northing);
		// Get the writer Factories from connect
		Collection<Class<OTFWriterFactory<VisLink>>> linkFactories =  connect.getQueueLinkEntries();
		List<OTFWriterFactory<VisLink>> linkWriterFactoriyObjects = new ArrayList<OTFWriterFactory<VisLink>>(linkFactories.size());
		try {
			OTFWriterFactory<VisLink> linkWriterFac = null;
			for (Class linkFactory : linkFactories ) {
				if(linkFactory != Object.class) {
					linkWriterFac = (OTFWriterFactory<VisLink>)linkFactory.newInstance();
					linkWriterFactoriyObjects.add(linkWriterFac);
				}
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

    	if(!linkWriterFactoriyObjects.isEmpty()) {
    		boolean first = true;
    		for (VisLink link : this.net.getVisLinks().values()) {
    			double middleEast = (link.getLink().getToNode().getCoord().getX() + link.getLink().getFromNode().getCoord().getX())*0.5 - this.minEasting;
    			double middleNorth = (link.getLink().getToNode().getCoord().getY() + link.getLink().getFromNode().getCoord().getY())*0.5 - this.minNorthing;

    			for (OTFWriterFactory<VisLink> fac : linkWriterFactoriyObjects) {
    				OTFDataWriter<VisLink> writer = fac.getWriter();
    				// null means take the default handler
    				if (writer != null) {
    					writer.setSrc(link);
    					if (first) {
    						log.info("Connecting Source QueueLink with " + writer.getClass().getName());
    						first = false;
    					}
    				}
    				put(middleEast, middleNorth, writer);
//      		System.out.println("server link: " + link.getId().toString() + " coords " + middleEast + "," + middleNorth );
    			}
    		}

   	}
	}

	@Override
	public void addAdditionalElement(OTFDataWriter element) {
		this.additionalElements.add(element);
	}

	@Override
	public OTFClientQuad convertToClient(String id, final OTFServerRemote host, final OTFConnectionManager connect) {
		final OTFClientQuad client = new OTFClientQuad(id, host, 0.,0.,this.maxEasting - this.minEasting, this.maxNorthing - this.minNorthing);
		client.offsetEast = this.minEasting;
		client.offsetNorth = this.minNorthing;

		//int colls =
		this.execute(0.,0.,this.maxEasting - this.minEasting,this.maxNorthing - this.minNorthing,
				new ConvertToClientExecutor(connect,client));
//		System.out.print("server executor count: " +colls );

		for(OTFDataWriter element : this.additionalElements) {
			Collection<Class<OTFDataReader>> readerClasses = connect.getReadersForWriter(element.getClass());
			for (Class readerClass : readerClasses) {
				try {
					Object reader = readerClass.newInstance();
					client.addAdditionalElement((OTFDataReader)reader);
					log.info("Connected additional element writer " + element.getClass().getName() + "(" + element + ")  to " + reader.getClass().getName() + " (" + reader + ")");
				} catch (InstantiationException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}

		}
		return client;
	}

	@Override
	public void writeConstData(ByteBuffer out) {
		//int colls =
		this.execute(0.,0.,this.maxEasting - this.minEasting,this.maxNorthing - this.minNorthing,
				new WriteDataExecutor(out,true));

		for(OTFDataWriter element : this.additionalElements) {
			try {
				element.writeConstData(out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void writeDynData(QuadTree.Rect bounds, ByteBuffer out) {
		//int colls =
		this.execute(bounds, new WriteDataExecutor(out,false));
		//System.out.print("# of Writes: " + colls + " -> ");

		for(OTFDataWriter element : this.additionalElements) {
			try {
				element.writeDynData(out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Internally we hold the coordinates from 0,0 to max -min .. to optimize use of float in visualizer
	@Override
	public double getMaxEasting() {
		return this.maxEasting;
	}

	@Override
	public double getMaxNorthing() {
		return this.maxNorthing;
	}

	@Override
	public double getMinEasting() {
		return this.minEasting;
	}

	@Override
	public double getMinNorthing() {
		return this.minNorthing;
	}

	public void replaceSrc(VisNetwork newNet) {
		//int colls =
		this.execute(0.,0.,this.maxEasting - this.minEasting,this.maxNorthing - this.minNorthing,
				new ReplaceSourceExecutor(newNet));
	}

	@Override
	public void initQuadTree(OTFConnectionManager connect) {
		this.fillQuadTree(connect);
	}

}
