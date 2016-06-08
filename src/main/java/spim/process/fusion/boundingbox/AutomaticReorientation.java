package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.Visualize_Detections;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.fusion.export.ImgExport;
import spim.process.interestpointregistration.ChannelProcess;
import spim.vecmath.AxisAngle4d;
import spim.vecmath.Matrix4d;
import spim.vecmath.Point3d;
import spim.vecmath.Transform3D;
import spim.vecmath.Vector3d;

public class AutomaticReorientation extends BoundingBoxGUI
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
	List< ViewId > viewIdsToApply;
	
	public AutomaticReorientation( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	/**
	 * Called before the XML is potentially saved
	 */
	@Override
	public boolean cleanUp()
	{
		if ( reorientate == 0 || reorientate == 1 )
		{
			// the spimdata registrations were changed
			return true;
		}
		else if ( reorientate == 2 )
		{
			// remove the registrations we added
			if ( viewIdsToApply == null )
			{
				IOFunctions.println( "Something went wrong, the viewIdsToApply list is null." );
			}
			else
			{
				for ( final ViewId viewId : viewIdsToApply )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
					
					if ( !vd.isPresent() )
						continue;

					// get the registration
					final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
					final List< ViewTransform > vtl = r.getTransformList();
					vtl.remove( 0 );
					r.updateModel();
				}
			}
		}

		return this.changedSpimDataObject;
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
		final List< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess ); //spimData.getSequenceDescription().getAllChannelsOrdered();

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
					viewIdsToProcess,
					channel,
					"use any detections from" );

			if ( labels == null )
				return false;

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			if ( labelsWereReset && labels[ Interest_Point_Registration.defaultChannelLabels[ j ] ].contains( "bead" ) )
				Interest_Point_Registration.defaultChannelLabels[ j ] = labels.length - 2;

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] < labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			String ch = channel.getName().replace( ' ', '_' );
			gd.addChoice( "Interest_points_channel_" + ch, labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
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
		final double[] minF, maxF;

		if ( reorientate == 3 )
		{
			final Pair< double[], double[] > minmax = determineSizeSimple( channelsToUse, detections );

			if ( minmax == null )
				return false;

			minF = minmax.getA();
			maxF = minmax.getB();
		}
		else
		{
			final Pair< AffineTransform3D, double[] > pair = determineOptimalBoundingBox( channelsToUse, detections );

			if ( pair == null )
				return false;

			//
			// set the registration for all or some of the views
			//
			IOFunctions.println( "Final transformation model: " + pair.getA() );

			if ( reorientate == 0 ) // apply to all views
			{
				viewIdsToApply = SpimData2.getAllViewIdsSorted( spimData, spimData.getSequenceDescription().getViewSetupsOrdered(), spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() );
				IOFunctions.println( "Will be applied only to all views and remembered/saved to the XML." );
			}
			else // apply only to fused views
			{
				viewIdsToApply = viewIdsToProcess;

				if ( reorientate == 1 )
					IOFunctions.println( "Will be applied only to fused views and remembered/saved to the XML." );
				else
					IOFunctions.println( "Will be temporarily applied only to fused views (NOT remembered in the XML)." );
			}

			for ( final ViewId viewId : viewIdsToApply )
			{
				final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
				
				if ( !vd.isPresent() )
					continue;

				// get the registration
				final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
				r.preconcatenateTransform( new ViewTransformAffine( reorientationDescription, pair.getA() ) );
				r.updateModel();
			}

			minF = new double[]{ pair.getB()[ 0 ], pair.getB()[ 1 ], pair.getB()[ 2 ] };
			maxF = new double[]{ pair.getB()[ 3 ], pair.getB()[ 4 ], pair.getB()[ 5 ] };
		}

		IOFunctions.println( "Min (without addition): " + Util.printCoordinates( minF ) );
		IOFunctions.println( "Max (without addition): " + Util.printCoordinates( maxF ) );

		this.min = new int[ 3 ];
		this.max = new int[ 3 ];

		double addX = (maxF[ 0 ] - minF[ 0 ]) * ( percent/100.0 ) / 2;
		double addY = (maxF[ 1 ] - minF[ 1 ]) * ( percent/100.0 ) / 2;
		double addZ = (maxF[ 2 ] - minF[ 2 ]) * ( percent/100.0 ) / 2;

		final double add = Math.max( addX, Math.max( addY, addZ ) );

		this.min[ 0 ] = (int)Math.round( minF[ 0 ] - add );
		this.min[ 1 ] = (int)Math.round( minF[ 1 ] - add );
		this.min[ 2 ] = (int)Math.round( minF[ 2 ] - add );

		this.max[ 0 ] = (int)Math.round( maxF[ 0 ] + add );
		this.max[ 1 ] = (int)Math.round( maxF[ 1 ] + add );
		this.max[ 2 ] = (int)Math.round( maxF[ 2 ] + add );

		IOFunctions.println( "Min (with addition): " + Util.printCoordinates( this.min ) );
		IOFunctions.println( "Max (with addition): " + Util.printCoordinates( this.max ) );

		BoundingBoxGUI.defaultMin = this.min.clone();
		BoundingBoxGUI.defaultMax = this.max.clone();

		return super.queryParameters( fusion, imgExport );
	}

	/**
	 * 
	 * @param channelsToUse
	 * @param detections
	 * @return - the transformation and minX, minY, minZ, maxX, maxY, maxZ as Pair
	 */
	protected Pair< AffineTransform3D, double[] > determineOptimalBoundingBox( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final List< double[] > points = getAllDetectionsInGlobalCoordinates( channelsToUse, detections );

		if ( points.size() < 1 )
		{
			IOFunctions.println( "At least one point is required. Stopping" );
			return null;
		}

		// identify most distant points
		double[] p1 = points.get( 0 );
		double[] p2 = points.get( 1 );
		double maxDist = squareDistance( p1[ 0 ], p1[ 1 ], p1[ 2 ], p2[ 0 ], p2[ 1 ], p2[ 2 ] );

		for ( int i = 0; i < points.size() - 1; ++i )
			for ( int j = i + 1; j < points.size(); ++j )
			{
				final double[] a = points.get( i );
				final double[] b = points.get( j );

				final double d = squareDistance( a[ 0 ], a[ 1 ], a[ 2 ], b[ 0 ], b[ 1 ], b[ 2 ] );

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

		final Vector3d sv = new Vector3d( p2[ 0 ] - p1[ 0 ], p2[ 1 ] - p1[ 1 ], p2[ 2 ] - p1[ 2 ] );
		sv.normalize();

		IOFunctions.println(
				"Maximum distance: " + Math.sqrt( maxDist ) + "px between points " + Util.printCoordinates( p1 ) +
				" and " + Util.printCoordinates( p2 ) + ", vector=" + sv + ", volume=" +
				testAxis( sv, points ).getA() / (1024*1024) + "MiPixels." );

		final double[] vectorStep = new double[]{ 0.4, 0.2, 0.1, 0.05, 0.025, 0.01, 0.005, 0.001 };

		double minVolume = Double.MAX_VALUE;
		double[] minBoundingBox = null;

		for ( int stepIndex = 0; stepIndex < vectorStep.length; ++stepIndex )
		{
			final double step = vectorStep[ stepIndex ];

			// the best search vector found so far on this scale
			final Vector3d bestSV = new Vector3d( sv );

			for ( int zi = -1; zi <= 1; ++zi )
				for ( int yi = -1; yi <= 1; ++yi )
					for ( int xi = -1; xi <= 1; ++xi )
					{
						// compute the test vector
						final Vector3d v = new Vector3d(
								sv.x + xi * step,
								sv.y + yi * step,
								sv.z + zi * step );

						v.normalize();

						final Pair< Double, double[] > volume = testAxis( v, points );

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
		final Vector3d xAxis = new Vector3d( 1, 0, 0 );
		final Matrix4d m = new Matrix4d();
		getRotation( sv, xAxis ).get( m );

		final AffineTransform3D a = new AffineTransform3D();
		a.set( m.m00, m.m01, m.m02, m.m03, m.m10, m.m11, m.m12, m.m13, m.m20, m.m21, m.m22, m.m23 );

		return new ValuePair< AffineTransform3D, double[] >( a, minBoundingBox );
	}

	

	/**
	 * Test one major axis for the minimal bounding box volume required
	 * 
	 * @param v - normalized vector
	 * @param points - all points
	 * @return - the volume and minX, minY, minZ, maxX, maxY, maxZ as Pair
	 */
	protected Pair< Double, double[] > testAxis( final Vector3d v, final List< double[] > points )
	{
		// mapping v onto the x axis
		final Vector3d xAxis = new Vector3d( 1, 0, 0 );
		final Transform3D t = getRotation( v, xAxis );

		final Point3d tmp = new Point3d();

		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;

		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;

		for ( final double[] p : points )
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

		return new ValuePair< Double, double[] >(
				( maxX - minX ) * ( maxY - minY ) * ( maxZ - minZ ),
				new double[]{ minX, minY, minZ, maxX, maxY, maxZ } );
	}

	/**
	 * Computes a Transform3D that will rotate vector v0 into the direction of vector v1.
	 * Note: vectors MUST be normalized for this to work!
	 *
	 * @param v0
	 * @param v1
	 * @return
	 */
	public static Transform3D getRotation( final Vector3d v0, final Vector3d v1 )
	{
		// the rotation axis is defined by the cross product
		final Vector3d rotAxis = new Vector3d();
		rotAxis.cross( v0, v1 );
		rotAxis.normalize();

		// if the cross product returns NaN, the vectors already point in the same direction,
		// so we return the identity transform
		if ( Double.isNaN( rotAxis.x ) )
			return new Transform3D();

		// the rotation angle is defined by the dot product (if normalized)
		final double angle = v0.dot( v1 );

		// Do an axis/angle 3d transformation
		final Transform3D t = new Transform3D();
		t.set( new AxisAngle4d( rotAxis, Math.acos( angle ) ) );

		return t;
	}

	final static public double squareDistance( final double p1x, final double p1y, final double p1z, final double p2x, final double p2y, final double p2z )
	{
		final double dx = p1x - p2x;
		final double dy = p1y - p2y;
		final double dz = p1z - p2z;
		
		return dx*dx + dy*dy + dz*dz;
	}

	protected Pair< double[], double[] > determineSizeSimple( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final List< double[] > points = getAllDetectionsInGlobalCoordinates( channelsToUse, detections );

		if ( points.size() < 1 )
		{
			IOFunctions.println( "At least one point is required. Stopping" );
			return null;
		}

		final double[] min = points.get( 0 ).clone();
		final double[] max = min.clone();

		for ( final double[] p : points )
			for ( int d = 0; d < p.length; ++d )
			{
				min[ d ] = Math.min( min[ d ], p[ d ] );
				max[ d ] = Math.max( max[ d ], p[ d ] );
			}

		IOFunctions.println( "Min (direct): " + Util.printCoordinates( min ) );
		IOFunctions.println( "Max (direct): " + Util.printCoordinates( max ) );

		return new ValuePair< double[], double[] >( min, max );
	}

	protected List< double[] > getAllDetectionsInGlobalCoordinates( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final ArrayList< double[] > ipList = new ArrayList< double[] >();

		for ( final ChannelProcess c : channelsToUse )
			for ( final ViewDescription vd : SpimData2.getAllViewIdsForChannelSorted( spimData, viewIdsToProcess, c.getChannel() ) )
			{
				if ( !vd.isPresent() )
					continue;

				// get the registration
				final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( vd );
				r.updateModel();
				final AffineTransform3D transform = r.getModel();

				// get the list of detections
				final ViewInterestPointLists vipl = spimData.getViewInterestPoints().getViewInterestPointLists( vd );
				final InterestPointList ipl = vipl.getInterestPointList( c.getLabel() );

				if ( ipl.getInterestPoints() == null )
					ipl.loadInterestPoints();

				final List< InterestPoint > list = ipl.getInterestPoints();

				// use all detections
				if ( detections == 0 )
				{
					for ( final InterestPoint p : list )
					{
						final double[] source = p.getL();
						final double[] target = new double[ source.length ];
						transform.apply( source, target );
						ipList.add( target );
					}
				}
				else // use only those who have correspondences
				{
					if ( ipl.getCorrespondingInterestPoints() == null )
						ipl.loadCorrespondingInterestPoints();

					final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

					for ( final InterestPoint ip : list )
						map.put( ip.getId(), ip );

					final List< CorrespondingInterestPoints > list2 = ipl.getCorrespondingInterestPoints();

					for ( final CorrespondingInterestPoints cp : list2 )
					{
						final InterestPoint p = map.get( cp.getDetectionId() );
						final double[] source = p.getL();
						final double[] target = new double[ source.length ];
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

		for ( final ViewId viewId : viewIdsToProcess )
		{
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
	public AutomaticReorientation newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new AutomaticReorientation( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Automatically reorientate and estimate based on sample features";
	}
}
