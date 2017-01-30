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

import java.util.ArrayList;
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
import mpicbg.spim.segmentation.InteractiveDoG;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.plugin.util.GenericDialogAppender;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;
import spim.process.cuda.CUDATools;
import spim.process.cuda.NativeLibraryTools;
import spim.process.interestpointdetection.ProcessDOG;

public class DifferenceOfGaussian extends DifferenceOf implements GenericDialogAppender
{
	public static double defaultUseGPUMem = 75;

	public static double defaultS = 1.8;
	public static double defaultT = 0.008;

	public static double defaultSigma[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];

	public static String[] computationOnChoice = new String[]{ "CPU (Java)", "GPU approximate (Nvidia CUDA via JNA)", "GPU accurate (Nvidia CUDA via JNA)" };
	public static int defaultComputationChoiceIndex = 0;

	double[] sigma;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;

	double percentGPUMem = defaultUseGPUMem;

	/**
	 * 0 ... n == CUDA device i
	 */
	ArrayList< CUDADevice > deviceList = null;
	CUDASeparableConvolution cuda = null;
	boolean accurateCUDA = false;

	public DifferenceOfGaussian( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussian newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new DifferenceOfGaussian( spimData, viewIdsToProcess );
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
				// compute Difference-of-Gaussian
				//
				List< InterestPoint > ips = 
					ProcessDOG.compute(
						cuda,
						deviceList,
						accurateCUDA,
						percentGPUMem,
						img,
						(Img<net.imglib2.type.numeric.real.FloatType>)input,
						(float)sigma[ c.getId() ],
						(float)threshold[ c.getId() ],
						localization,
						Math.min( imageSigmaX, (float)sigma[ c.getId() ] ),
						Math.min( imageSigmaY, (float)sigma[ c.getId() ] ),
						Math.min( imageSigmaZ, (float)sigma[ c.getId() ] ),
						findMin[ c.getId() ],
						findMax[ c.getId() ],
						minIntensity,
						maxIntensity,
						limitDetections );

				img.close();

				correctForDownsampling( ips, correctCoordinates );

				if ( limitDetections )
					ips = limitList( maxDetections, maxDetectionsTypeIndex, ips );

				interestPoints.put( vd, ips );

				benchmark.computation += System.currentTimeMillis() - time2;
			}
			catch ( Exception  e )
			{
				IOFunctions.println( "An error occured (DOG): " + e ); 
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
		
		this.sigma[ channelId ] = defaultS;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;

		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.001;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.008;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.03;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.1;
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
		gd.addNumericField( "Sigma" + ch, defaultSigma[ channelId ], 5 );
		gd.addNumericField( "Threshold" + ch, defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima" + ch, defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima" + ch, defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.sigma[ channelId ] = defaultSigma[ channelId ] = gd.getNextNumber();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Gaussian", "Please select view to use for channel " + channel.getName(), channel );
		
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
		imp.setRoi( 0, 0, imp.getWidth()/3, imp.getHeight()/3 );		

		final InteractiveDoG idog = new InteractiveDoG( imp );
		final int channelId = channel.getId();

		idog.setSigma2isAdjustable( false );
		idog.setInitialSigma( (float)defaultSigma[ channelId ] );
		idog.setThreshold( (float)defaultThreshold[ channelId ] );
		idog.setLookForMinima( defaultFindMin[ channelId ] );
		idog.setLookForMaxima( defaultFindMax[ channelId ] );
		idog.setMinIntensityImage( minIntensity ); // if is Double.NaN will be ignored
		idog.setMaxIntensityImage( maxIntensity ); // if is Double.NaN will be ignored

		idog.run( null );
		
		while ( !idog.isFinished() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e) {}
		}

		imp.close();

		if ( idog.wasCanceled() )
			return false;

		this.sigma[ channelId ] = defaultSigma[ channelId ] = idog.getInitialSigma();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = idog.getThreshold();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = idog.getLookForMinima();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = idog.getLookForMaxima();
		
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
		this.sigma = new double[ numChannels ];
		this.threshold = new double[ numChannels ];
		this.findMin = new boolean[ numChannels ];
		this.findMax = new boolean[ numChannels ];

		if ( defaultSigma == null || defaultSigma.length != numChannels )
		{
			defaultSigma = new double[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultSigma[ c ] = defaultS;
				defaultThreshold[ c ] = defaultT;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}

	@Override
	public String getParameters( final int channelId )
	{
		return "DOG s=" + sigma[ channelId ] + " t=" + threshold[ channelId ] + " min=" + findMin[ channelId ] + " max=" + findMax[ channelId ] +
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXY=" + downsampleXY +
				" downsampleZ=" + downsampleZ + " additionalSigmaX=" + additionalSigmaX  + " additionalSigmaY=" + additionalSigmaY + 
				" additionalSigmaZ=" + additionalSigmaZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputationChoiceIndex ] );
		
	}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd )
	{
		final int computationTypeIndex = defaultComputationChoiceIndex = gd.getNextChoiceIndex();

		if ( computationTypeIndex == 1 )
			accurateCUDA = false;
		else
			accurateCUDA = true;

		if ( computationTypeIndex >= 1 )
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "separable" );
			
			cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDASeparableConvolution.class );

			if ( cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				deviceList = null;
				return false;
			}
			else
			{
				deviceList = new ArrayList< CUDADevice >();
			}

			// multiple CUDA devices sometimes crashes, no idea why yet ...
			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( cuda, false, this );

			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;
			else
				deviceList.addAll( selectedDevices );

			// TODO: remove this, only for debug on non-CUDA machines >>>>
			if ( deviceList.get( 0 ).getDeviceName().startsWith( "CPU emulation" ) )
			{
				for ( int i = 0; i < deviceList.size(); ++i )
				{
					deviceList.set( i, new CUDADevice( -1-i, deviceList.get( i ).getDeviceName(), deviceList.get( i ).getTotalDeviceMemory(), deviceList.get( i ).getFreeDeviceMemory(), deviceList.get( i ).getMajorComputeVersion(), deviceList.get( i ).getMinorComputeVersion() ) );
					IOFunctions.println( "Running on cpu emulation, added " + ( -1-i ) + " as device" );
				}
			}
			// TODO: <<<< remove this, only for debug on non-CUDA machines
		}
		else
		{
			deviceList = null;
		}

		return true;
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addMessage( "" );
		gd.addSlider( "Percent_of_GPU_Memory_to_use", 1, 100, defaultUseGPUMem );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.percentGPUMem = defaultUseGPUMem = gd.getNextNumber();
		return true;
	}
}
