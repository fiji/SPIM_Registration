package spim.process.fusion.export;

import ij.gui.GenericDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.export.ExportSpimData2TIFF.FileNamePattern;
import bdv.img.hdf5.Hdf5ImageLoader;

public class AppendSpimData2 implements ImgExport
{
	public static String defaultPath = null;

	List< TimePoint > newTimepoints;
	List< ViewSetup > newViewSetups;

	Save3dTIFF saver;
	SpimData2 spimData;

	AppendSpimData2HDF5 appendToHdf5 = null;

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs )
	{
		if ( appendToHdf5 != null )
			return appendToHdf5.exportImage( img, bb, tp, vs );

		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs, final double min, final double max )
	{
		if ( appendToHdf5 != null )
			return appendToHdf5.exportImage( img, bb, tp, vs, min, max );

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
		if ( appendToHdf5 != null )
			return appendToHdf5.finish();

		// this spimdata object was modified
		return true;
	}

	@Override
	public void setXMLData ( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		if ( appendToHdf5 != null )
			appendToHdf5.setXMLData( newTimepoints, newViewSetups );

		this.newTimepoints = newTimepoints;
		this.newViewSetups = newViewSetups;
	}

	@Override
	public boolean queryParameters( final SpimData2 spimData, final boolean is16bit )
	{
		if ( spimData.getSequenceDescription().getImgLoader() instanceof Hdf5ImageLoader )
		{
			appendToHdf5 = new AppendSpimData2HDF5();
			appendToHdf5.setXMLData( newTimepoints, newViewSetups );
			return appendToHdf5.queryParameters( spimData, is16bit );
		}

		this.spimData = spimData;

		StackImgLoaderIJ loader;

		if ( spimData.getSequenceDescription().getImgLoader() instanceof StackImgLoaderIJ )
		{
			loader = (StackImgLoaderIJ)spimData.getSequenceDescription().getImgLoader();
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

		appendSpimData2( spimData, newTimepoints, newViewSetups );

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
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{
		if ( appendToHdf5 != null )
			appendToHdf5.queryAdditionalParameters( gd, spimData );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{
		if ( appendToHdf5 != null )
			return appendToHdf5.parseAdditionalParameters( gd, spimData );

		return true;
	}

	@Override
	public ImgExport newInstance()
	{
		return new AppendSpimData2();
	}

	@Override
	public String getDescription()
	{
		return "Append to current XML Project";
	}

	/**
	 * Assembles a new SpimData2 based on the timepoints and viewsetups.
	 * The imgloader is still not set here.
	 *
	 */
	public static void appendSpimData2(
			final SpimData2 spimData,
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > newViewSetups )
	{
		final SequenceDescription sequenceDescription = spimData.getSequenceDescription();

		// current viewsetups
		final Map< Integer, ViewSetup > viewSetups = (Map< Integer, ViewSetup >)sequenceDescription.getViewSetups();

		// add all the newly fused viewsetups
		for ( final ViewSetup vs : newViewSetups )
			viewSetups.put( vs.getId(), vs );

		resetViewSetupsAndDescriptions( sequenceDescription );

		// all the timepoints that are not processed are missing views
		final HashSet< ViewId > newMissingViews = new HashSet< ViewId >();
		final Map< ViewId, ViewInterestPointLists > ips = spimData.getViewInterestPoints().getViewInterestPoints();
		for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
		{
			if ( !timepointsToProcess.contains( t ) )
			{
				for ( final ViewSetup newSetup : newViewSetups )
					newMissingViews.add( new ViewId( t.getId(), newSetup.getId() ) );
			}
			else
			{
				for ( final ViewSetup newSetup : newViewSetups )
					ips.put( new ViewId( t.getId(), newSetup.getId() ), new ViewInterestPointLists( t.getId(), newSetup.getId() ) );
			}
		}

		// are there new ones? if so extend the maybe not existant list
		if ( newMissingViews.size() > 0 )
		{
			MissingViews m = spimData.getSequenceDescription().getMissingViews();
			if ( m != null )
				newMissingViews.addAll( m.getMissingViews() );
			m = new MissingViews( newMissingViews );

			// marking the missing views
			setMissingViews( spimData.getSequenceDescription(), m );
			BasicViewDescription.markMissingViews( spimData.getSequenceDescription().getViewDescriptions(), m );
		}

		// add the viewregistrations to the exisiting ones
		final Map< ViewId, ViewRegistration > regMap = spimData.getViewRegistrations().getViewRegistrations();

		for ( final TimePoint tp : timepointsToProcess )
			for ( final ViewSetup vs : newViewSetups )
			{
				final ViewDescription vd = sequenceDescription.getViewDescription( tp.getId(), vs.getId() );
				final ViewRegistration viewRegistration = new ViewRegistration( vd.getTimePointId(), vd.getViewSetupId() );
				viewRegistration.identity();
				regMap.put( vd, viewRegistration );
			}
	}

	private static final void resetViewSetupsAndDescriptions( final SequenceDescription s )
	{
		try
		{
			Class< ? > clazz = null;
			Field viewSetupsOrderedDirty = null;
			Field viewDescriptionsDirty = null;

			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();

				if ( clazz != null )
					for ( final Field field : clazz.getDeclaredFields() )
					{
						if ( field.getName().equals( "viewSetupsOrderedDirty" ) )
							viewSetupsOrderedDirty = field;

						if ( field.getName().equals( "viewDescriptionsDirty" ) )
							viewDescriptionsDirty = field;
					}
			}
			while ( ( viewSetupsOrderedDirty == null || viewDescriptionsDirty == null ) && clazz != null );

			if ( viewDescriptionsDirty == null || viewDescriptionsDirty == null )
			{
				System.out.println( "Failed to find SequenceDescription.viewSetupsOrderedDirty or SequenceDescription.viewDescriptionsDirty field. Quiting." );
				return;
			}

			viewSetupsOrderedDirty.setAccessible( true );
			viewSetupsOrderedDirty.set( s, true );
			viewDescriptionsDirty.setAccessible( true );
			viewDescriptionsDirty.set( s, true );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}

	private static final void setMissingViews( final SequenceDescription s, final MissingViews m )
	{
		try
		{
			Class< ? > clazz = null;
			boolean found = false;

			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();

				if ( clazz != null )
					for ( final Method method : clazz.getDeclaredMethods() )
						if ( method.getName().equals( "setMissingViews" ) )
							found = true;
			}
			while ( !found && clazz != null );

			if ( !found )
			{
				System.out.println( "Failed to find SequenceDescription.setMissingViews method. Quiting." );
				return;
			}

			final Method setMissingViews = clazz.getDeclaredMethod( "setMissingViews", MissingViews.class );
			setMissingViews.setAccessible( true );
			setMissingViews.invoke( s, m );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}
}
