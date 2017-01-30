/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
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
package spim.fiji.plugin.interestpointdetection;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.HashMap;
import java.util.List;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveIntegral;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointdetection.ProcessDOM;


public class DifferenceOfMean extends DifferenceOf
{
	public static int defaultR1 = 2;
	public static int defaultR2 = 3;
	public static double defaultT = 0.02;
	
	public static int defaultRadius1[];
	public static int defaultRadius2[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];
	
	int[] radius1;
	int[] radius2;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;
	
	public DifferenceOfMean( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Mean (Integral image based)"; }

	@Override
	public DifferenceOfMean newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{ 
		return new DifferenceOfMean( spimData, viewIdsToProcess );
	}

	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				//
				// open the corresponding image (if present at this timepoint)
				//
				long time1 = System.currentTimeMillis();

				if ( !vd.isPresent() )
					continue;

				final Channel c = vd.getViewSetup().getChannel();

				final AffineTransform3D correctCoordinates = new AffineTransform3D();
				final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = openAndDownsample( spimData, vd, correctCoordinates );

				long time2 = System.currentTimeMillis();

				benchmark.openFiles += time2 - time1;

				preSmooth( input );
				
				final Image< FloatType > img = ImgLib2.wrapFloatToImgLib1( (Img<net.imglib2.type.numeric.real.FloatType>)input );

				//
				// compute Difference-of-Mean
				//
				List< InterestPoint > ips =
					ProcessDOM.compute(
						img,
						(Img<net.imglib2.type.numeric.real.FloatType>)input,
						radius1[ c.getId() ],
						radius2[ c.getId() ],
						(float)threshold[ c.getId() ],
						localization,
						imageSigmaX,
						imageSigmaY,
						imageSigmaZ,
						findMin[ c.getId() ],
						findMax[ c.getId() ],
						minIntensity,
						maxIntensity,
						limitDetections);

				img.close();

				correctForDownsampling( ips, correctCoordinates );

				if ( limitDetections )
					ips = limitList( maxDetections, maxDetectionsTypeIndex, ips );

				interestPoints.put( vd, ips );

				benchmark.computation += System.currentTimeMillis() - time2;
			}
			catch ( Exception  e )
			{
				IOFunctions.println( "An error occured (DOM): " + e ); 
				IOFunctions.println( "Failed to segment angleId: " + 
						vd.getViewSetup().getAngle().getId() + " channelId: " +
						vd.getViewSetup().getChannel().getId() + " illumId: " +
						vd.getViewSetup().getIllumination().getId() + ". Continuing with next one." );
				e.printStackTrace();
			}
		}

		return interestPoints;
	}
	
	@Override
	protected boolean setDefaultValues( final Channel channel, final int brightness )
	{
		final int channelId = channel.getId();
		
		this.radius1[ channelId ] = defaultR1;
		this.radius2[ channelId ] = defaultR2;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;
		
		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.0025f;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.02f;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.075f;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.25f;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues( final Channel channel )
	{
		final int channelId = channel.getId();
		
		final GenericDialog gd = new GenericDialog( "Advanced values for channel " + channel.getName() );

		String ch;

		if ( this.channelsToProcess.size() > 1 )
			ch = "_" + channel.getName().replace( ' ', '_' );
		else
			ch = "";

		gd.addMessage( "Advanced values for channel " + channel.getName() );
		gd.addNumericField( "Radius_1" + ch, defaultRadius1[ channelId ], 0 );
		gd.addNumericField( "Radius_2" + ch, defaultRadius2[ channelId ], 0 );
		gd.addNumericField( "Threshold" + ch, defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima" + ch, defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima" + ch, defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.radius1[ channelId ] = defaultRadius1[ channelId ] = (int)Math.round( gd.getNextNumber() );
		this.radius2[ channelId ] = defaultRadius2[ channelId ] = (int)Math.round( gd.getNextNumber() );
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Mean", "Please select view to use for channel " + channel.getName(), channel );
		
		if ( view == null )
			return false;

		final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( view.getTimePointId(), view.getViewSetupId() );
		
		if ( !viewDescription.isPresent() )
		{
			IOFunctions.println( "You defined the view you selected as not present at this timepoint." );
			IOFunctions.println( "timepoint: " + viewDescription.getTimePoint().getName() + 
								 " angle: " + viewDescription.getViewSetup().getAngle().getName() + 
								 " channel: " + viewDescription.getViewSetup().getChannel().getName() + 
								 " illum: " + viewDescription.getViewSetup().getIllumination().getName() );
			return false;
		}
		
		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > img =
				openAndDownsample( spimData, viewDescription, new AffineTransform3D() );

		if ( img == null )
		{
			IOFunctions.println( "View not found: " + viewDescription );
			return false;
		}
		
		preSmooth( img );
		
		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "" ).duplicate();
		img = null;
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setTitle( "tp: " + viewDescription.getTimePoint().getName() + " viewSetup: " + viewDescription.getViewSetupId() );		
		imp.show();		
		imp.setSlice( imp.getStackSize() / 2 );	
		
		final InteractiveIntegral ii = new InteractiveIntegral();
		final int channelId = channel.getId();	
		
		ii.setInitialRadius( Math.round( defaultRadius1[ channelId ] ) );
		ii.setThreshold( (float)defaultThreshold[ channelId ] );
		ii.setLookForMinima( defaultFindMin[ channelId ] );
		ii.setLookForMaxima( defaultFindMax[ channelId ] );
		ii.setMinIntensityImage( minIntensity ); // if is Double.NaN will be ignored
		ii.setMaxIntensityImage( maxIntensity ); // if is Double.NaN will be ignored

		ii.run( null );
		
		while ( !ii.isFinished() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e) {}
		}

		imp.close();

		if ( ii.wasCanceld() )
			return false;

		this.radius1[ channelId ] = defaultRadius1[ channelId ] = ii.getRadius1();
		this.radius2[ channelId ] = defaultRadius2[ channelId ] = ii.getRadius2();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = ii.getThreshold();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = ii.getLookForMinima();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = ii.getLookForMaxima();

		return true;
	}
	

	/**
	 * This is only necessary to make static objects so that the ImageJ dialog remembers choices
	 * for the right channel
	 * 
	 * @param numChannels - the TOTAL number of channels (not only the ones to process)
	 */
	@Override
	protected void init( final int numChannels )
	{
		radius1 = new int[ numChannels ];
		radius2 = new int[ numChannels ];
		threshold = new double[ numChannels ];
		findMin = new boolean[ numChannels ];
		findMax = new boolean[ numChannels ];

		if ( defaultRadius1 == null || defaultRadius1.length != numChannels )
		{
			defaultRadius1 = new int[ numChannels ];
			defaultRadius2 = new int[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultRadius1[ c ] = defaultR1;
				defaultRadius2[ c ] = defaultR2;
				defaultThreshold[ c ] = defaultT;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}
	
	@Override
	public String getParameters( final int channelId )
	{
		return "DOM r1=" + radius1[ channelId ] + " t=" + threshold[ channelId ] + " min=" + findMin[ channelId ] + " max=" + findMax[ channelId ] + 
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXY=" + downsampleXY +
				" downsampleZ=" + downsampleZ + " additionalSigmaX=" + additionalSigmaX  + " additionalSigmaY=" + additionalSigmaY + 
				" additionalSigmaZ=" + additionalSigmaZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd ) {}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd ) { return true; }
}
