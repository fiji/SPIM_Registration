package spim.fiji.plugin.interestpointdetection;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveIntegral;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.headless.interestpointdetection.DoM;
import spim.headless.interestpointdetection.DoMParameters;
import spim.headless.interestpointdetection.DownsampleTools;


public class DifferenceOfMeanGUI extends DifferenceOfGUI
{
	public static int defaultRadius1 = 2;
	public static int defaultRadius2 = 3;
	public static double defaultThreshold = 0.005;
	public static boolean defaultFindMin;
	public static boolean defaultFindMax;
	
	int radius1;
	int radius2;
	double threshold;
	boolean findMin;
	boolean findMax;
	
	public DifferenceOfMeanGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Mean (Integral image based)"; }

	@Override
	public DifferenceOfMeanGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{ 
		return new DifferenceOfMeanGUI( spimData, viewIdsToProcess );
	}

	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final DoMParameters dom = new DoMParameters();

		dom.imgloader = spimData.getSequenceDescription().getImgLoader();
		dom.toProcess = new ArrayList< ViewDescription >();

		dom.localization = this.localization;
		dom.downsampleXY = this.downsampleXY;
		dom.downsampleZ = this.downsampleZ;
		dom.imageSigmaX = this.imageSigmaX;
		dom.imageSigmaY = this.imageSigmaY;
		dom.imageSigmaZ = this.imageSigmaZ;

		dom.radius1 = this.radius1;
		dom.radius2 = this.radius2;
		dom.threshold = (float)this.threshold;
		dom.findMin = this.findMin;
		dom.findMax = this.findMax;

		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				if ( !vd.isPresent() )
					continue;

				dom.toProcess.clear();
				dom.toProcess.add( vd );

				DoM.addInterestPoints( interestPoints, dom );
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
	protected boolean setDefaultValues( final int brightness )
	{
		this.radius1 = defaultRadius1;
		this.radius2 = defaultRadius2;
		this.findMin = false;
		this.findMax = true;
		
		if ( brightness == 0 )
			this.threshold = 0.0025f;
		else if ( brightness == 1 )
			this.threshold = 0.02f;
		else if ( brightness == 2 )
			this.threshold = 0.075f;
		else if ( brightness == 3 )
			this.threshold = 0.25f;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues()
	{
		final GenericDialog gd = new GenericDialog( "Advanced values" );

		gd.addNumericField( "Radius_1", defaultRadius1, 0 );
		gd.addNumericField( "Radius_2", defaultRadius2, 0 );
		gd.addNumericField( "Threshold", defaultThreshold, 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin );
		gd.addCheckbox( "Find_maxima", defaultFindMax );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.radius1 = defaultRadius1 = (int)Math.round( gd.getNextNumber() );
		this.radius2 = defaultRadius2 = (int)Math.round( gd.getNextNumber() );
		this.threshold = defaultThreshold = gd.getNextNumber();
		this.findMin = defaultFindMin = gd.getNextBoolean();
		this.findMax = defaultFindMax = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues()
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Mean", "Please select view to use" );
		
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
				DownsampleTools.openAndDownsample(
						spimData.getSequenceDescription().getImgLoader(),
						viewDescription,
						new AffineTransform3D(),
						this.downsampleXY,
						this.downsampleZ );

		if ( img == null )
		{
			IOFunctions.println( "View not found: " + viewDescription );
			return false;
		}

		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "" ).duplicate();
		img = null;
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setTitle( "tp: " + viewDescription.getTimePoint().getName() + " viewSetup: " + viewDescription.getViewSetupId() );
		imp.show();
		imp.setSlice( imp.getStackSize() / 2 );
		
		final InteractiveIntegral ii = new InteractiveIntegral();

		ii.setInitialRadius( Math.round( defaultRadius1 ) );
		ii.setThreshold( (float)defaultThreshold );
		ii.setLookForMinima( defaultFindMin );
		ii.setLookForMaxima( defaultFindMax );
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

		this.radius1 = defaultRadius1 = ii.getRadius1();
		this.radius2 = defaultRadius2 = ii.getRadius2();
		this.threshold = defaultThreshold = ii.getThreshold();
		this.findMin = defaultFindMin = ii.getLookForMinima();
		this.findMax = defaultFindMax = ii.getLookForMaxima();

		return true;
	}

	@Override
	public String getParameters( final int channelId )
	{
		return "DOM r1=" + radius1 + " t=" + threshold + " min=" + findMin + " max=" + findMax + 
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXY=" + downsampleXY +
				" downsampleZ=" + downsampleZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd ) {}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd ) { return true; }
}
