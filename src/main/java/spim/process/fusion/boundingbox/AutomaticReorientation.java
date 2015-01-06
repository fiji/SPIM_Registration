package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.media.j3d.Transform3D;
import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import mpicbg.imglib.util.Util;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.Visualize_Detections;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.fusion.export.ImgExport;
import spim.process.interestpointregistration.ChannelProcess;

public class AutomaticReorientation extends ManualBoundingBox
{
	public static enum Reorientation { NONE, PARTLY, ALL };

	public static String reorientationDescription = "Reorientation to minimize bounding box";
	public String[] reorientationChoice = new String[]{
			"Automatically reorientate the sample (and store transformation for all views in the XML)",
			"Automatically reorientate the sample (and store transformation only for fused views in the XML)",
			"Automatically reorientate the sample (just temporarily, do NOT store transformation in XML)",
			"Do NOT reorientate the sample" };

	public static int defaultReorientate = 0;
	public static boolean defaultSaveReorientation = true;
	public static int defaultDetections = 1;
	public static double defaultPercent = 10;

	int reorientate;

	public AutomaticReorientation(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	/**
	 * Called before the XML is potentially saved
	 */
	public void cleanUp( LoadParseQueryXML result )
	{
		if ( reorientate == 0 || reorientate == 1 )
		{
			// save the xml
			final XmlIoSpimData2 io = new XmlIoSpimData2();
			
			final String xml = new File( result.getData().getBasePath(), new File( result.getXMLFileName() ).getName() ).getAbsolutePath();
			try 
			{
				io.save( result.getData(), xml );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xml + "' (applied the transformation to mimimize the bounding box)." );
			}
			catch ( Exception e )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xml + "': " + e );
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		GenericDialog gd = new GenericDialog( "Bounding Box Definition" );

		// ask for what do do
		final Pair< Integer, Integer > reorientated = numReorientated();

		String note;

		if ( reorientated.getA() == 0 )
			note = "None";
		else if ( reorientated.getA().intValue() == reorientated.getB().intValue() )
			note = "All";
		else
			note = reorientated.getA().intValue() + " of " + reorientated.getB().intValue();

		gd.addMessage( "Note: " + note + " of the views are already reorientated.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addChoice( "Reorientation", reorientationChoice, reorientationChoice[ defaultReorientate ] );

		// ask which detections to use
		gd.addMessage( "" );
		gd.addMessage( "Note: The bounding box is estimated based on detections in the image, choose which ones to use.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		// check which channels and labels are available and build the choices
		// ask which channels have the objects we are searching for
		final List< Channel > channels = spimData.getSequenceDescription().getAllChannelsOrdered();

		boolean labelsWereReset = false;

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != channels.size() )
		{
			Interest_Point_Registration.defaultChannelLabels = new int[ channels.size() ];
			labelsWereReset = true;
		}

		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(
					spimData,
					timepointsToProcess,
					anglesToProcess,
					illumsToProcess,
					channel,
					"use any detections from" );

			if ( labels == null )
				return false;

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			if ( labelsWereReset && labels[ Interest_Point_Registration.defaultChannelLabels[ j ] ].contains( "bead" ) )
				Interest_Point_Registration.defaultChannelLabels[ j ] = labels.length - 1;

			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
			channelLabels.add( labels );
		}

		gd.addChoice( "Use", Visualize_Detections.detectionsChoice, Visualize_Detections.detectionsChoice[ defaultDetections ] );

		// details about the bounding box
		gd.addMessage( "" );
		gd.addMessage(
				"Note: The detections themselves should not lie on the edge of the bounding box, please define how\n" +
				"much bigger [in percent of the largest dimension] the bounding box should be. If you want to define it\n" +
				"in pixels, use the following dialog and put 0% here.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addSlider( "Additional_size [%]", 0, 100, defaultPercent );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		this.reorientate = defaultReorientate = gd.getNextChoiceIndex();

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToUse = new ArrayList< ChannelProcess >();
		j = 0;

		for ( final Channel channel : channels )
		{
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( j ).length - 1 )
			{
				String label = channelLabels.get( j )[ channelChoice ];
				
				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );
				
				channelsToUse.add( new ChannelProcess( channel, label ) );
			}

			++j;
		}
		
		if ( channelsToUse.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return false;
		}

		final int detections = defaultDetections = gd.getNextChoiceIndex();
		final double percent = defaultPercent = gd.getNextNumber();

		for ( final ChannelProcess c : channelsToUse )
			IOFunctions.println( "using from channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "', " + (detections == 0 ? "all detections." : "only corresponding detections.") );

		// to be filled
		final float[] minF, maxF;

		if ( reorientate == 3 )
		{
			final Pair< float[], float[] > minmax = determineSizeSimple( channelsToUse, detections );

			if ( minmax == null )
				return false;

			minF = minmax.getA();
			maxF = minmax.getB();
		}
		else
		{
			final Pair< AffineTransform3D, float[] > pair = determineOptimalBoundingBox( channelsToUse, detections );

			if ( pair == null )
				return false;

			//
			// set the registration for all or some of the views
			//
			IOFunctions.println( "Final transformation model: " + pair.getA() );

			final List< TimePoint > tps;
			final List< Channel > chns;
			final List< Illumination > ills;
			final List< Angle > angls;

			if ( reorientate == 0 ) // apply to all views
			{
				tps = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered();
				chns = spimData.getSequenceDescription().getAllChannelsOrdered();
				ills = spimData.getSequenceDescription().getAllIlluminationsOrdered();
				angls = spimData.getSequenceDescription().getAllAnglesOrdered();

				IOFunctions.println( "Will be applied only to all views and saved to the XML." );
			}
			else // apply only to fused views
			{
				tps = timepointsToProcess;
				chns = channelsToProcess;
				ills = illumsToProcess;
				angls = anglesToProcess;

				if ( reorientate == 1 )
					IOFunctions.println( "Will be applied only to fused views and saved to the XML." );
				else
					IOFunctions.println( "Will be temporarily applied only to fused views (NOT saved to XML)." );
			}

			for ( final TimePoint t : tps )
				for ( final Channel c : chns )
					for ( final Illumination i : ills )
						for ( final Angle a : angls )
						{
							final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

							// this happens only if a viewsetup is not present in any timepoint
							// (e.g. after appending fusion to a dataset)
							if ( viewId == null )
								continue;

							final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
							
							if ( !vd.isPresent() )
								continue;

							// get the registration
							final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
							r.preconcatenateTransform( new ViewTransformAffine( reorientationDescription, pair.getA() ) );
							r.updateModel();
						}

			minF = new float[]{ pair.getB()[ 0 ], pair.getB()[ 1 ], pair.getB()[ 2 ] };
			maxF = new float[]{ pair.getB()[ 3 ], pair.getB()[ 4 ], pair.getB()[ 5 ] };
		}

		IOFunctions.println( "Min (without addition): " + Util.printCoordinates( minF ) );
		IOFunctions.println( "Max (without addition): " + Util.printCoordinates( maxF ) );

		final int[] min = new int[ 3 ];
		final int[] max = new int[ 3 ];

		float addX = (maxF[ 0 ] - minF[ 0 ]) * (float)( percent/100.0 ) / 2;
		float addY = (maxF[ 1 ] - minF[ 1 ]) * (float)( percent/100.0 ) / 2;
		float addZ = (maxF[ 2 ] - minF[ 2 ]) * (float)( percent/100.0 ) / 2;

		final float add = Math.max( addX, Math.max( addY, addZ ) );

		min[ 0 ] = Math.round( minF[ 0 ] - add );
		min[ 1 ] = Math.round( minF[ 1 ] - add );
		min[ 2 ] = Math.round( minF[ 2 ] - add );

		max[ 0 ] = Math.round( maxF[ 0 ] + add );
		max[ 1 ] = Math.round( maxF[ 1 ] + add );
		max[ 2 ] = Math.round( maxF[ 2 ] + add );

		IOFunctions.println( "Min (with addition): " + Util.printCoordinates( min ) );
		IOFunctions.println( "Max (with addition): " + Util.printCoordinates( max ) );

		BoundingBox.defaultMin = min;
		BoundingBox.defaultMax = max;

		return super.queryParameters( fusion, imgExport );
	}

	/**
	 * 
	 * @param channelsToUse
	 * @param detections
	 * @return - the transformation and minX, minY, minZ, maxX, maxY, maxZ as Pair
	 */
	protected Pair< AffineTransform3D, float[] > determineOptimalBoundingBox( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final List< float[] > points = getAllDetectionsInGlobalCoordinates( channelsToUse, detections );

		if ( points.size() < 1 )
		{
			IOFunctions.println( "At least one point is required. Stopping" );
			return null;
		}

		// identify most distant points
		float[] p1 = points.get( 0 );
		float[] p2 = points.get( 1 );
		double maxDist = squareDistance( p1[ 0 ], p1[ 1 ], p1[ 2 ], p2[ 0 ], p2[ 1 ], p2[ 2 ] );

		for ( int i = 0; i < points.size() - 1; ++i )
			for ( int j = i + 1; j < points.size(); ++j )
			{
				final float[] a = points.get( i );
				final float[] b = points.get( j );

				final float d = squareDistance( a[ 0 ], a[ 1 ], a[ 2 ], b[ 0 ], b[ 1 ], b[ 2 ] );

				if ( d > maxDist )
				{
					maxDist = d;
					if ( a[ 0 ] <= b[ 0 ] )
					{
						p1 = a;
						p2 = b;
					}
					else
					{
						p1 = b;
						p2 = a;
					}
				}
			}

		final Vector3f sv = new Vector3f( p2[ 0 ] - p1[ 0 ], p2[ 1 ] - p1[ 1 ], p2[ 2 ] - p1[ 2 ] );
		sv.normalize();

		IOFunctions.println(
				"Maximum distance: " + Math.sqrt( maxDist ) + "px between points " + Util.printCoordinates( p1 ) +
				" and " + Util.printCoordinates( p2 ) + ", vector=" + sv + ", volume=" +
				testAxis( sv, points ).getA() / (1024*1024) + "MiPixels." );

		final float[] vectorStep = new float[]{ 0.4f, 0.2f, 0.1f, 0.05f, 0.025f, 0.01f, 0.005f, 0.001f };

		double minVolume = Double.MAX_VALUE;
		float[] minBoundingBox = null;

		for ( int stepIndex = 0; stepIndex < vectorStep.length; ++stepIndex )
		{
			final float step = vectorStep[ stepIndex ];

			// the best search vector found so far on this scale
			final Vector3f bestSV = new Vector3f( sv );

			for ( int zi = -1; zi <= 1; ++zi )
				for ( int yi = -1; yi <= 1; ++yi )
					for ( int xi = -1; xi <= 1; ++xi )
					{
						// compute the test vector
						final Vector3f v = new Vector3f(
								sv.x + xi * step,
								sv.y + yi * step,
								sv.z + zi * step );

						v.normalize();

						final Pair< Double, float[] > volume = testAxis( v, points );

						if ( volume.getA() < minVolume )
						{
							minVolume = volume.getA();
							minBoundingBox = volume.getB();
							bestSV.set( v );

							IOFunctions.println( "Scale: " + step + " --- Min Volume: " + minVolume / (1024*1024) + "MiPixels, vector=" + bestSV );
						}
					}

			// update the search vector to the best solution from this scale
			sv.set( bestSV );
		}

		// final mapping v onto the x axis
		final Vector3f xAxis = new Vector3f( 1, 0, 0 );
		final Matrix4f m = new Matrix4f();
		getRotation( sv, xAxis ).get( m );

		final AffineTransform3D a = new AffineTransform3D();
		a.set( m.m00, m.m01, m.m02, m.m03, m.m10, m.m11, m.m12, m.m13, m.m20, m.m21, m.m22, m.m23 );

		return new ValuePair< AffineTransform3D, float[] >( a, minBoundingBox );
	}

	

	/**
	 * Test one major axis for the minimal bounding box volume required
	 * 
	 * @param v - normalized vector
	 * @param points - all points
	 * @return - the volume and minX, minY, minZ, maxX, maxY, maxZ as Pair
	 */
	protected Pair< Double, float[] > testAxis( final Vector3f v, final List< float[] > points )
	{
		// mapping v onto the x axis
		final Vector3f xAxis = new Vector3f( 1, 0, 0 );
		final Transform3D t = getRotation( v, xAxis );

		final Point3f tmp = new Point3f();

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;

		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;

		for ( final float[] p : points )
		{
			// transform onto the x-axis
			tmp.set( p[ 0 ], p[ 1 ], p[ 2 ] );
			t.transform( tmp );

			minX = Math.min( minX, tmp.x );
			minY = Math.min( minY, tmp.y );
			minZ = Math.min( minZ, tmp.z );

			maxX = Math.max( maxX, tmp.x );
			maxY = Math.max( maxY, tmp.y );
			maxZ = Math.max( maxZ, tmp.z );
		}

		return new ValuePair< Double, float[] >(
				(double)( maxX - minX ) * (double)( maxY - minY ) * (double)( maxZ - minZ ),
				new float[]{ minX, minY, minZ, maxX, maxY, maxZ } );
	}

	/**
	 * Computes a Transform3D that will rotate vector v0 into the direction of vector v1.
	 * Note: vectors MUST be normalized for this to work!
	 *
	 * @param v0
	 * @param v1
	 * @return
	 */
	public static Transform3D getRotation( final Vector3f v0, final Vector3f v1 )
	{
		// the rotation axis is defined by the cross product
		final Vector3f rotAxis = new Vector3f();
		rotAxis.cross( v0, v1 );
		rotAxis.normalize();

		// if the cross product returns NaN, the vectors already point in the same direction,
		// so we return the identity transform
		if ( Float.isNaN( rotAxis.x ) )
			return new Transform3D();

		// the rotation angle is defined by the dot product (if normalized)
		final float angle = v0.dot( v1 );

		// Do an axis/angle 3d transformation
		final Transform3D t = new Transform3D();
		t.set( new AxisAngle4f( rotAxis, (float)Math.acos( angle ) ) );

		return t;
	}
	final static public float squareDistance( final float p1x, final float p1y, final float p1z, final float p2x, final float p2y, final float p2z )
	{
		final double dx = p1x - p2x;
		final double dy = p1y - p2y;
		final double dz = p1z - p2z;
		
		return ( float )( dx*dx + dy*dy + dz*dz );
	}

	protected Pair< float[], float[] > determineSizeSimple( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final List< float[] > points = getAllDetectionsInGlobalCoordinates( channelsToUse, detections );

		if ( points.size() < 1 )
		{
			IOFunctions.println( "At least one point is required. Stopping" );
			return null;
		}

		final float[] min = points.get( 0 ).clone();
		final float[] max = min.clone();

		for ( final float[] p : points )
			for ( int d = 0; d < p.length; ++d )
			{
				min[ d ] = Math.min( min[ d ], p[ d ] );
				max[ d ] = Math.max( max[ d ], p[ d ] );
			}

		IOFunctions.println( "Min (direct): " + Util.printCoordinates( min ) );
		IOFunctions.println( "Max (direct): " + Util.printCoordinates( max ) );

		return new ValuePair< float[], float[] >( min, max );
	}

	protected List< float[] > getAllDetectionsInGlobalCoordinates( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final ArrayList< float[] > ipList = new ArrayList< float[] >();

		for ( final TimePoint t : timepointsToProcess )
			for ( final ChannelProcess c : channelsToUse )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c.getChannel(), a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( !vd.isPresent() )
							continue;

						// get the registration
						final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
						r.updateModel();
						final AffineTransform3D transform = r.getModel();

						// get the list of detections
						final ViewInterestPointLists vipl = spimData.getViewInterestPoints().getViewInterestPointLists( viewId );
						final InterestPointList ipl = vipl.getInterestPointList( c.getLabel() );

						ipl.loadInterestPoints();

						final List< InterestPoint > list = ipl.getInterestPoints();

						// use all detections
						if ( detections == 0 )
						{
							for ( final InterestPoint p : list )
							{
								final float[] source = p.getL();
								final float[] target = new float[ source.length ];
								transform.apply( source, target );
								ipList.add( target );
							}
						}
						else // use only those who have correspondences
						{
							ipl.loadCorrespondingInterestPoints();

							final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

							for ( final InterestPoint ip : list )
								map.put( ip.getId(), ip );

							final List< CorrespondingInterestPoints > list2 = ipl.getCorrespondingInterestPoints();

							for ( final CorrespondingInterestPoints cp : list2 )
							{
								final InterestPoint p = map.get( cp.getDetectionId() );
								final float[] source = p.getL();
								final float[] target = new float[ source.length ];
								transform.apply( source, target );
								ipList.add( target );
							}
						}
					}

		return ipList;
	}

	protected Pair< Integer, Integer > numReorientated()
	{
		final ViewRegistrations vrs = spimData.getViewRegistrations();

		int isReorientated = 0;
		int sumViews = 0;

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( !vd.isPresent() )
							continue;

						final ViewRegistration vr = vrs.getViewRegistration( viewId );
						final ViewTransform vt = vr.getTransformList().get( 0 );

						++sumViews;

						if ( vt.hasName() && vt.getName().startsWith( reorientationDescription ) )
								++isReorientated;
					}

		return new ValuePair< Integer, Integer >( isReorientated, sumViews );
	}

	@Override
	public AutomaticReorientation newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess)
	{
		return new AutomaticReorientation( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Automatically reorientate and estimate based on sample features";
	}
}
