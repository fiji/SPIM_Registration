package spim.process.fusion.export;

import ij.gui.GenericDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.PluginHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.process.fusion.export.ExportSpimData2TIFF.FileNamePattern;
import fiji.util.gui.GenericDialogPlus;

public class AppendSpimData2 implements ImgExport
{
	public static String defaultPath = null;

	List< TimePoint > newTimepoints;
	List< ViewSetup > newViewSetups;

	Save3dTIFF saver;
	SpimData2 spimData;

	String xmlFile;

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
	public boolean finish()
	{
		try
		{
			new XmlIoSpimData2().save( spimData, new File( xmlFile ).getAbsolutePath() );
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xmlFile + "'." );
			return true;
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xmlFile + "'." );
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
	public boolean queryParameters( final SpimData2 oldSpimData )
	{
		StackImgLoaderIJ loader;
		
		if ( oldSpimData.getSequenceDescription().getImgLoader() instanceof StackImgLoaderIJ )
		{
			loader = (StackImgLoaderIJ)oldSpimData.getSequenceDescription().getImgLoader();
		}
		else
		{
			IOFunctions.println( "Appending is currently only supported for ImageJ TIFF based SpimData XML projects." );
			return false;
		}
		
		if ( newTimepoints == null || newViewSetups == null )
		{
			IOFunctions.println( "New timepoints and new viewsetup list not set yet ... cannot continue" );
			return false;
		}

		final GenericDialogPlus gd = new GenericDialogPlus( "Append dataset using TIFF" );

		if ( defaultPath == null )
			defaultPath = LoadParseQueryXML.defaultXMLfilename;

		PluginHelper.addSaveAsFileField( gd, "Select new XML", defaultPath, 80 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		xmlFile = gd.getNextString();
		
		if ( !xmlFile.endsWith( ".xml" ) )
			xmlFile += ".xml";

		defaultPath = LoadParseQueryXML.defaultXMLfilename = xmlFile;
		
		this.spimData = appendSpimData2( oldSpimData, newTimepoints, newViewSetups );

		final boolean compress = loader.getFileNamePattern().endsWith( ".zip" );
		
		if ( compress )
			IOFunctions.println( "Compression is ON" );
		else
			IOFunctions.println( "Compression is OFF" );
		
		this.saver = new Save3dTIFF( this.spimData.getBasePath().toString(), compress );
		this.saver.setImgTitler( new XMLTIFFImgTitler( this.spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered(), this.spimData.getSequenceDescription().getViewSetupsOrdered() ) );

		// adjust the imgloader (concatenate both basically, can be two different patterns now in the worst case - if we for example now have two illumination directions and before only one)
		final FileNamePattern fnp = ExportSpimData2TIFF.getFileNamePattern( this.spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered(), this.spimData.getSequenceDescription().getViewSetupsOrdered(), compress );

		final String newFileNamePattern;
		
		if ( loader.getFileNamePattern().equals( fnp.fileNamePattern ) )
			newFileNamePattern = fnp.fileNamePattern;
		else
			newFileNamePattern = loader.getFileNamePattern() + ";" + fnp.fileNamePattern;
		
		final StackImgLoaderIJ newLoader = new StackImgLoaderIJ( 
				loader.getPath(),
				newFileNamePattern, 
				loader.getImgFactory(), 
				Math.max( loader.getLayoutTimePoints(), fnp.layoutTP ),
				Math.max( loader.getLayoutChannels(), fnp.layoutChannels ),
				Math.max( loader.getLayoutIlluminations(), fnp.layoutIllum ),
				Math.max( loader.getLayoutAngles(), fnp.layoutAngles ),
				this.spimData.getSequenceDescription() );
		
		this.spimData.getSequenceDescription().setImgLoader( newLoader );
		
		return true;
	}

	@Override
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) {}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) { return true; }

	@Override
	public ImgExport newInstance() { return new AppendSpimData2(); }

	@Override
	public String getDescription() { return "Append to current XML Project"; }

	/**
	 * Assembles a new SpimData2 based on the timepoints and viewsetups.
	 * The imgloader is still not set here.
	 * 
	 * @param params
	 * @return
	 */
	public static SpimData2 appendSpimData2(
			final SpimData2 spimData,
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > newViewSetups )
	{
		// same timepoints as before
		final TimePoints timepoints = spimData.getSequenceDescription().getTimePoints();

		// same views are still missing
		final ArrayList< ViewId > missingViews = new ArrayList< ViewId >();
		if ( spimData.getSequenceDescription().getMissingViews() != null )
			missingViews.addAll( spimData.getSequenceDescription().getMissingViews().getMissingViews() );

		final List< ViewSetup > viewSetups = spimData.getSequenceDescription().getViewSetupsOrdered();

		int maxId = -1;

		for ( final ViewSetup vs : viewSetups )
			maxId = Math.max( maxId, vs.getId() );

		for ( final ViewSetup vs : newViewSetups )
			maxId = Math.max( maxId, vs.getId() );
		
		// all the timepoints that are not processed are missing views
		for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
		{
			if ( !timepointsToProcess.contains( t ) )
			{
				for ( final ViewSetup newSetup : newViewSetups )
					missingViews.add( new ViewId( t.getId(), newSetup.getId() ) );
			}
		}

		// add all the newly fused viewsetups
		viewSetups.addAll( newViewSetups );

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetups, spimData.getSequenceDescription().getImgLoader(), new MissingViews( missingViews ) );

		// add the viewregistrations to the exisiting ones
		final Map< ViewId, ViewRegistration > regMap = spimData.getViewRegistrations().getViewRegistrations();
		
		for ( final TimePoint tp : timepointsToProcess )
			for ( final ViewSetup vs : newViewSetups )
			{
				final ViewDescription vd = sequenceDescription.getViewDescription( tp.getId(), vs.getId() );
				final ViewRegistration viewRegistration = new ViewRegistration( vd.getTimePointId(), vd.getViewSetupId() );
				viewRegistration.identity();
				regMap.put( viewRegistration, viewRegistration );
			}

		final SpimData2 newSpimData = new SpimData2(
				spimData.getBasePath(),
				sequenceDescription,
				new ViewRegistrations( regMap ),
				spimData.getViewInterestPoints() );

		return newSpimData;
	}

}
