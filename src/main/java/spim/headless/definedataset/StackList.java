package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.spimdata.NamePattern;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import spim.fiji.spimdata.imgloaders.MicroManagerImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Base StackList class for various kinds of DataSet definition class
 */
public class StackList extends DefineDataSet
{
	protected static class Calibration
	{
		public double calX = 1, calY = 1, calZ = 1;
		public String calUnit = "um";

		public Calibration( final double calX, final double calY, final double calZ, final String calUnit )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
			this.calUnit = calUnit;
		}

		public Calibration( final double calX, final double calY, final double calZ )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
		}

		public Calibration() {};
	}

	protected static class ViewSetupPrecursor
	{
		final public int c, i, a;
		final public ArrayList< String> channelNameList, illuminationsNameList, angleNameList;

		public ViewSetupPrecursor( final int c, final int i, final int a, ArrayList< String> channelNameList, ArrayList< String>  illuminationsNameList, ArrayList< String>  angleNameList )
		{
			this.c = c;
			this.i = i;
			this.a = a;

			this.channelNameList = channelNameList;
			this.illuminationsNameList = illuminationsNameList;
			this.angleNameList = angleNameList;
		}

		@Override
		public int hashCode()
		{
			return c * illuminationsNameList.size() * angleNameList.size() + i * angleNameList.size() + a;
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof ViewSetupPrecursor )
				return c == ((ViewSetupPrecursor)o).c && i == ((ViewSetupPrecursor)o).i && a == ((ViewSetupPrecursor)o).a;
			else
				return false;
		}

		@Override
		public String toString() { return "channel=" + channelNameList.get( c ) + ", ill.dir.=" + illuminationsNameList.get( i ) + ", angle=" + angleNameList.get( a ); }
	}

	protected static String assembleDefaultPattern(final int hasMultipleAngles, final int hasMultipleTimePoints, final int hasMultipleChannels, final int hasMultipleIlluminations)
	{
		String pattern = "spim";

		if ( hasMultipleTimePoints == 1 )
			pattern += "_TL{t}";

		if ( hasMultipleChannels == 1 )
			pattern += "_Channel{c}";

		if ( hasMultipleIlluminations == 1 )
			pattern += "_Illum{i}";

		if ( hasMultipleAngles == 1 )
			pattern += "_Angle{a}";

		return pattern + ".tif";
	}

	/**
	 * Create sequence description for StackList.
	 *
	 * @param timepoints the timepoints
	 * @param channels the channels
	 * @param illuminations the illuminations
	 * @param angles the angles
	 * @param calibration the calibration
	 * @return the sequence description
	 */
	public static SequenceDescription createSequenceDescription( final String timepoints, final String channels, final String illuminations, final String angles, Calibration calibration )
	{
		// assemble timepints, viewsetups, missingviews and the imgloader
		TimePoints timePoints = null;
		try
		{
			timePoints = new TimePointsPattern( timepoints );
		}
		catch ( ParseException e )
		{
			e.printStackTrace();
		}

		ArrayList< String > timepointNameList = null, channelNameList = null, illuminationsNameList = null, angleNameList = null;

		try
		{
			timepointNameList = ( NamePattern.parseNameString( timepoints, false ) );
			channelNameList = ( NamePattern.parseNameString( channels, true ) );
			illuminationsNameList = ( NamePattern.parseNameString( illuminations, true ) );
			angleNameList = ( NamePattern.parseNameString( angles, true ) );
		}
		catch ( ParseException e )
		{
			e.printStackTrace();
		}


		final ArrayList< Channel > channelList = new ArrayList< Channel >();
		for ( int c = 0; c < channelNameList.size(); ++c )
			channelList.add( new Channel( c, channelNameList.get( c ) ) );

		final ArrayList< Illumination > illuminationList = new ArrayList< Illumination >();
		for ( int i = 0; i < illuminationsNameList.size(); ++i )
			illuminationList.add( new Illumination( i, illuminationsNameList.get( i ) ) );

		final ArrayList< Angle > angleList = new ArrayList< Angle >();
		for ( int a = 0; a < angleNameList.size(); ++a )
			angleList.add( new Angle( a, angleNameList.get( a ) ) );

		HashMap< ViewSetupPrecursor, Calibration > calibrations = new HashMap< ViewSetupPrecursor, Calibration >();

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channelList )
			for ( final Illumination i : illuminationList )
				for ( final Angle a : angleList )
				{
					final Calibration cal = calibrations.get( new ViewSetupPrecursor( c.getId(), i.getId(), a.getId(), channelNameList, illuminationsNameList, angleNameList ) );
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( cal.calUnit, cal.calX, cal.calY, cal.calZ );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, null, voxelSize, c, a, i ) );
				}

		ArrayList< int[] > exceptionIds = new ArrayList< int[] >();

		// Add exceptionIDs
		//
		// exceptionIds.add( new int[]{ t, c, i, a } );
		// System.out.println( "adding missing views t:" + t + " c:" + c + " i:" + i + " a:" + a );

		if ( exceptionIds.size() == 0 )
			return null;

		final ArrayList< ViewId > missingViewArray = new ArrayList< ViewId >();

		if ( exceptionIds.size() > 0 )
		{
			for ( int t = 0; t < timepointNameList.size(); ++t )
			{
				// assemble a subset of exceptions for the current timepoint
				final ArrayList< int[] > tmp = new ArrayList< int[] >();

				for ( int[] exceptions : exceptionIds )
					if ( exceptions[ 0 ] == t )
						tmp.add( exceptions );

				if ( tmp.size() > 0 )
				{
					int setupId = 0;

					for ( int c = 0; c < channelNameList.size(); ++c )
						for ( int i = 0; i < illuminationsNameList.size(); ++i )
							for ( int a = 0; a < angleNameList.size(); ++a )
							{
								for ( int[] exceptions : tmp )
									if ( exceptions[ 1 ] == c && exceptions[ 2 ] == i && exceptions[ 3 ] == a )
									{
										missingViewArray.add( new ViewId( Integer.parseInt( timepointNameList.get( t ) ), setupId ) );
										System.out.println( "creating missing views t:" + Integer.parseInt( timepointNameList.get( t ) ) + " c:" + c + " i:" + i + " a:" + a + " setupid: " + setupId );
									}

								++setupId;
							}
				}
			}
		}

		final MissingViews missingViews = new MissingViews( missingViewArray );		// instantiate the sequencedescription
		return new SequenceDescription( timePoints, viewSetups, null, missingViews );
	}
}
