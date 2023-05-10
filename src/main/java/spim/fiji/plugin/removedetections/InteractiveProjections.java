/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.plugin.removedetections;

import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;

import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.fusion.deconvolution.ExtractPSF;

public class InteractiveProjections
{
	public static double size = 2;

	final Frame frame;

	protected boolean isRunning, wasCanceled;
	protected ImagePlus imp;
	protected List< InterestPoint > ipList;
	final protected List< Thread > runAfterFinished;

	public InteractiveProjections( final SpimData2 spimData, final ViewDescription vd, final String label, final String newLabel, final int projectionDim )
	{
		this.isRunning = true;
		this.wasCanceled = false;
		this.runAfterFinished = new ArrayList< Thread >();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + ": Loading image ..." );
		RandomAccessibleInterval< FloatType > img = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), false );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + ": Computing max projection along dimension " + projectionDim + " ..." );
		final Img< FloatType > maxProj = ExtractPSF.computeMaxProjection( img, new ArrayImgFactory< FloatType >(), projectionDim );
		this.imp = showProjection( maxProj );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + ": Loading & drawing interest points ..." );
		this.ipList = loadInterestPoints( spimData, vd, label );
		drawProjectedInterestPoints( imp, ipList, projectionDim );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + ": " + ipList.size() + " points displayed ... " );

		frame = new Frame( "Remove detections" );
		frame.setSize( 300, 180 );

		/* Instantiation */
		final GridBagLayout layout = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();

		final Button removeIn = new Button( "Remove all detections INside ROI" );
		final Button removeOut = new Button( "Remove all detections OUTside ROI" );
		final Button done = new Button( "Done" );
		final Button cancel = new Button( "Cancel" );

		/* Location */
		frame.setLayout( layout );

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;

		frame.add( removeIn, c );

		++c.gridy;
		frame.add( removeOut, c );

		c.insets = new Insets( 20,0,0,0 );
		++c.gridy;
		frame.add( done, c );

		c.insets = new Insets( 0,0,0,0 );
		++c.gridy;
		frame.add( cancel, c );

		removeIn.addActionListener( new RemoveInsideROIButtonListener( imp, ipList, projectionDim, true ) );
		removeOut.addActionListener( new RemoveInsideROIButtonListener( imp, ipList, projectionDim, false ) );
		done.addActionListener( new FinishedButtonListener( frame, false ) );
		cancel.addActionListener( new FinishedButtonListener( frame, true ) );

		frame.setVisible( true );
	}

	public void runWhenDone( final Thread thread ) { this.runAfterFinished.add( thread ); }
	public List< InterestPoint > getInterestPointList() { return ipList; }
	public boolean isRunning() { return isRunning; }
	public boolean wasCanceled() { return wasCanceled; }

	protected static void drawProjectedInterestPoints( final ImagePlus imp, final List< InterestPoint > ipList, final int projectionDim )
	{
		final int xDim = getXDim( projectionDim );
		final int yDim = getYDim( projectionDim );

		// extract peaks to show
		Overlay o = imp.getOverlay();

		if ( o == null )
		{
			o = new Overlay();
			imp.setOverlay( o );
		}

		o.clear();

		for ( final InterestPoint ip : ipList )
		{
			final double x = ip.getL()[ xDim ];
			final double y = ip.getL()[ yDim ];
			
				final OvalRoi or = new OvalRoi( Math.round( x - size ), Math.round( y - size ), Math.round( size * 2 ), Math.round( size * 2 ) );
				or.setStrokeColor( Color.green );
				o.add( or );
		}

		imp.updateAndDraw();
	}

	protected static int getXDim( final int projectionDim )
	{
		if ( projectionDim == 2 )
			return 0;
		else if ( projectionDim == 1 )
			return 0;
		else
			return 1;
	}

	protected static int getYDim( final int projectionDim )
	{
		if ( projectionDim == 2 )
			return 1;
		else if ( projectionDim == 1 )
			return 2;
		else
			return 2;
	}

	protected List< InterestPoint > loadInterestPoints( final SpimData2 spimData, final ViewId id, final String label )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( id );
		final InterestPointList list = lists.getInterestPointList( label );

		if ( list.getInterestPoints() == null )
			list.loadInterestPoints();

		final ArrayList< InterestPoint > newList = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : list.getInterestPoints() )
			newList.add( new InterestPoint( p.getId(), p.getL().clone() ) );

		return newList;
	}

	protected ImagePlus showProjection( final Img< FloatType > img )
	{
		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "Max Projection" );
		imp.show();
		return imp;
	}

	protected void close( final Frame parent )
	{
		if ( parent != null )
			parent.dispose();

		if ( imp != null )
			imp.close();

		for ( final Thread t : runAfterFinished )
			t.start();

		isRunning = false;
	}

	protected class RemoveInsideROIButtonListener implements ActionListener
	{
		final ImagePlus imp;
		final List< InterestPoint > ipList;
		final int projectionDim, xDim, yDim;
		final boolean inside;

		public RemoveInsideROIButtonListener( final ImagePlus imp, final List< InterestPoint > ipList, final int projectionDim, final boolean inside )
		{
			this.imp = imp;
			this.ipList = ipList;
			this.projectionDim = projectionDim;
			this.inside = inside;
			this.xDim = getXDim( projectionDim );
			this.yDim = getYDim( projectionDim );
		}

		@Override
		public void actionPerformed( final ActionEvent arg0 )
		{
			final Roi roi = imp.getRoi();

			if ( roi == null )
			{
				IOFunctions.println( "No ROI selected in max projection image." );
			}
			else
			{
				int count = ipList.size();

				for ( int i = ipList.size() - 1; i >= 0; --i )
				{
					final double[] l = ipList.get( i ).getL();

					final boolean contains = roi.contains( (int)Math.round( l[ xDim ] ), (int)Math.round( l[ yDim ] ) );
					
					if ( inside && contains || !inside && !contains )
						ipList.remove( i );
				}

				drawProjectedInterestPoints( imp, ipList, projectionDim );

				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + ": " + ipList.size() + " points remaining, removed " + (count - ipList.size()) + " points ... " );
			}
		}
		
	}

	protected class FinishedButtonListener implements ActionListener
	{
		final Frame parent;
		final boolean frameWasCanceled;

		public FinishedButtonListener( final Frame parent, final boolean frameWasCanceled )
		{
			this.parent = parent;
			this.frameWasCanceled = frameWasCanceled;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{ 
			close( parent );
			wasCanceled = this.frameWasCanceled;
		}
	}

}
