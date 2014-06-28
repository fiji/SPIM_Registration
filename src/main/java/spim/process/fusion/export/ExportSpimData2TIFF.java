package spim.process.fusion.export;

import ij.gui.GenericDialog;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.resave.Resave_TIFF;
import spim.fiji.plugin.resave.Resave_TIFF.Parameters;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class ExportSpimData2TIFF implements ImgExport
{
	List< TimePoint > newTimepoints;
	List< ViewSetup > newViewSetups;

	Parameters params;
	Save3dTIFF saver;
	SpimData2 spimData;

	@Override
	public boolean finish()
	{
		try
		{
			new XmlIoSpimData2().save( spimData, new File( params.getXMLFile() ).getAbsolutePath() );
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.getXMLFile() + "'." );
			return true;
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.getXMLFile() + "'." );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void setXMLData ( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		this.newTimepoints = newTimepoints;
		this.newViewSetups = newViewSetups;
	}
	
	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBox bb, final TimePoint tp, final ViewSetup vs )
	{
		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBox bb, final TimePoint tp, final ViewSetup vs, final double min, final double max )
	{
		// write the image
		if ( !this.saver.exportImage( img, bb, tp, vs, min, max ) )
			return false;

		// update the registrations
		final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( new ViewId( tp.getId(), vs.getId() ) );
		
		final double scale = bb.getDownSampling();
		final AffineTransform3D m = new AffineTransform3D();
		m.set( scale, 0.0f, 0.0f, bb.min( 0 ), 
			   0.0f, scale, 0.0f, bb.min( 1 ),
			   0.0f, 0.0f, scale, bb.min( 2 ) );
		final ViewTransform vt = new ViewTransformAffine( "fusion bounding box", m );

		vr.getTransformList().clear();
		vr.getTransformList().add( vt );
		
		return true;
	}

	@Override
	public boolean queryParameters( final SpimData2 spimData )
	{
		if ( newTimepoints == null || newViewSetups == null )
		{
			IOFunctions.println( "new timepoints and new viewsetup list not set yet ... cannot continue" );
			return false;
		}

		if ( Resave_TIFF.defaultPath == null )
			Resave_TIFF.defaultPath = "";
		
		this.params = Resave_TIFF.getParameters();
		
		if ( this.params == null )
			return false;

		this.saver = new Save3dTIFF( new File( this.params.getXMLFile() ).getParent(), this.params.compress() );
		this.saver.setImgTitler( new XMLTIFFImgTitler( newTimepoints, newViewSetups ) );

		this.spimData = createSpimData2( newTimepoints, newViewSetups, params );

		return true;
	}

	@Override
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) {}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) { return true; }

	@Override
	public ImgExport newInstance() { return new ExportSpimData2TIFF(); }

	@Override
	public String getDescription() { return "Save as new XML Project (TIFF)"; }

	protected SpimData2 createSpimData2(
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > viewSetupsToProcess,
			final Parameters params )
	{
		int layoutTP = 0, layoutChannels = 0, layoutIllum = 0, layoutAngles = 0;
		String filename = "img";

		if ( timepointsToProcess.size() > 1 )
		{
			filename += "_TL{t}";
			layoutTP = 1;
		}
		
		if ( XMLTIFFImgTitler.getAllChannels( viewSetupsToProcess ).size() > 1 )
		{
			filename += "_Ch{c}";
			layoutChannels = 1;
		}
		
		if ( XMLTIFFImgTitler.getAllIlluminations( viewSetupsToProcess ).size() > 1 )
		{
			filename += "_Ill{i}";
			layoutIllum = 1;
		}
		
		if ( XMLTIFFImgTitler.getAllAngles( viewSetupsToProcess ).size() > 1 )
		{
			filename += "_Angle{a}";
			layoutAngles = 1;
		}

		filename += ".tif";

		if ( params.compress() )
			filename += ".zip";

		// Assemble a new SpimData object containing the subset of viewsetups and timepoints
		final SpimData2 newSpimData = assembleSpimData2( timepointsToProcess, viewSetupsToProcess, new File( params.getXMLFile() ).getParentFile() );

		final StackImgLoaderIJ imgLoader = new StackImgLoaderIJ(
				new File( params.getXMLFile() ).getParentFile(),
				filename, params.getImgFactory(),
				layoutTP, layoutChannels, layoutIllum, layoutAngles, null );
		newSpimData.getSequenceDescription().setImgLoader( imgLoader );

		return newSpimData;
	}

	/**
	 * Assembles a new SpimData2 based on the timepoints and viewsetups.
	 * The imgloader is still not set here.
	 * 
	 * @param params
	 * @return
	 */
	public static SpimData2 assembleSpimData2( 
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > viewSetupsToProcess,
			final File basePath )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
			e.printStackTrace();
			return null;
		}
		
		final MissingViews missingViews = new MissingViews( new ArrayList< ViewId >() );
				
		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetupsToProcess, null, missingViews );

		// assemble the viewregistrations
		final Map< ViewId, ViewRegistration > regMap = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewDescription vDesc : sequenceDescription.getViewDescriptions().values() )
		{
			final ViewRegistration viewRegistration = new ViewRegistration( vDesc.getTimePointId(), vDesc.getViewSetupId() );
			viewRegistration.identity();
			regMap.put( viewRegistration, viewRegistration );
		}
		
		final ViewRegistrations viewRegistrations = new ViewRegistrations( regMap );
		
		// assemble the interestpoints and a list of filenames to copy
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( new HashMap< ViewId, ViewInterestPointLists >() );

		final SpimData2 newSpimData = new SpimData2(
				basePath,
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints );

		return newSpimData;
	}
}
