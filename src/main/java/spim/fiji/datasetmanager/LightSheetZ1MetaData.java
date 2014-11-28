package spim.fiji.datasetmanager;

import java.util.HashMap;
import java.util.Hashtable;

import loci.formats.IFormatReader;
import loci.formats.Modulo;
import loci.formats.meta.MetadataRetrieve;
import mpicbg.spim.io.IOFunctions;
import ome.xml.model.primitives.PositiveFloat;

public class LightSheetZ1MetaData
{
	private String objective = "";
	private int rotationAxis = -1;
	private int channels[];
	private int angles[];
	private int numT = -1;
	private int numI = -1;
	private double calX, calY, calZ, lightsheetThickness = -1;
	private String[] files;
	private HashMap< Integer, int[] > imageSizes;

	public int numChannels() { return channels.length; }
	public int numAngles() { return angles.length; }
	public int numIlluminations() { return numI; }
	public int numTimepoints() { return numT; }
	public String objective() { return objective; }
	public int rotationAxis() { return rotationAxis; }
	public double calX() { return calX; }
	public double calY() { return calY; }
	public double calZ() { return calZ; }
	public String[] files() { return files; }
	public int[] channels() { return channels; }
	public int[] angles() { return angles; }
	public HashMap< Integer, int[] > imageSizes() { return imageSizes; }
	public String calUnit() { return "um"; }
	public double lightsheetThickness() { return lightsheetThickness; }
	public String rotationAxisName()
	{
		if ( rotationAxis == 0 )
			return "X";
		else if ( rotationAxis == 1 )
			return "Y";
		else if ( rotationAxis == 2 )
			return "Z";
		else
			return "Unknown";
	}

	public boolean loadMetaData( final IFormatReader r )
	{
		final Hashtable< String, Object > metaData = r.getGlobalMetadata();
		final int numA = r.getSeriesCount();

		// make sure every angle has the same amount of timepoints, channels, illuminations
		this.numT = -1;
		this.numI = -1;
		int numC = -1;
		
		// also collect the image sizes for each angle
		this.imageSizes = new HashMap< Integer, int[] >();

		try
		{
			for ( int a = 0; a < numA; ++a )
			{
				r.setSeries( a );

				int w = r.getSizeX();
				int h = r.getSizeY();
				int d = (int)Math.round( Double.parseDouble( metaData.get( "Information|Image|V|View|SizeZ #" + (a+1) ).toString() ) );

				imageSizes.put( a, new int[]{ w, h, d } );

				if ( numT >= 0 && numT != r.getSizeT() )
				{
					IOFunctions.println( "Number of timepoints inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numT = r.getSizeT();
				}
				
				// Illuminations are contained within the channel count; to
				// find the number of illuminations for the current angle:
				Modulo moduloC = r.getModuloC();

				if ( numI >= 0 && numI != moduloC.length() )
				{
					IOFunctions.println( "Number of illumination directions inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numI = moduloC.length();
				}

				if ( numC >= 0 && numC != r.getSizeC() / moduloC.length() )
				{
					IOFunctions.println( "Number of channels directions inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numC = r.getSizeC() / moduloC.length();
				}
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the main meta data: " + e + "\n. Stopping." );
			return false;
		}

		//
		// query non-essential details
		//
		this.channels = new int[ numC ];
		this.angles = new int[ numA ];
		this.files = r.getSeriesUsedFiles();

		Object tmp;

		try
		{
			tmp = metaData.get( "Experiment|AcquisitionBlock|AcquisitionModeSetup|Objective #1" );
			objective = (tmp != null) ? tmp.toString() : "Unknown Objective";

			for ( int c = 0; c < numC; ++c )
			{
				tmp = metaData.get( "Information|Image|Channel|Wavelength #" + ( c+1 ) );
				channels[ c ] = (tmp != null) ? (int)Math.round( Double.parseDouble( tmp.toString() ) ) : c;
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the objective used: " + e + "\n. Proceeding." );
			for ( int c = 0; c < numC; ++c )
				channels[ c ] = c;
		}

		try
		{
			boolean allAnglesNegative = true;

			for ( int a = 0; a < numA; ++a )
			{
				tmp = metaData.get( "Information|Image|V|View|Offset #" + ( a+1 ) );
				angles[ a ] = (tmp != null) ? (int)Math.round( Double.parseDouble( tmp.toString() ) ) : a;

				if ( angles[ a ] > 0 )
					allAnglesNegative = false;
			}

			if ( allAnglesNegative )
				for ( int a = 0; a < numA; ++a )
					angles[ a ] *= -1;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the rotation angles: " + e + "\n. Proceeding." );
			for ( int a = 0; a < numA; ++a )
				angles[ a ] = a;
		}

		try
		{
			tmp = metaData.get( "Information|Image|V|AxisOfRotation #1" );
			if ( tmp != null && tmp.toString().trim().length() == 5 )
			{
				final String[] axes = tmp.toString().split( " " );
				
				if ( Integer.parseInt( axes[ 0 ] ) == 1 )
					rotationAxis = 0;
				else if ( Integer.parseInt( axes[ 1 ] ) == 1 )
					rotationAxis = 1;
				else if ( Integer.parseInt( axes[ 2 ] ) == 1 )
					rotationAxis = 2;
				else
					rotationAxis = -1;
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the rotation axis: " + e + "\n. Proceeding." );
			rotationAxis = -1;
		}

		try
		{
			for ( final String key : metaData.keySet() )
			{
				if ( key.startsWith( "LsmTag|Name #" ) && metaData.get( key ).toString().trim().equals( "LightSheetThickness" ) )
				{
					String lookup = "LsmTag " + key.substring( key.indexOf( '#' ), key.length() );
					tmp = metaData.get( lookup );

					if ( tmp != null )
						lightsheetThickness = Double.parseDouble( tmp.toString() );
					else
						lightsheetThickness = -1;
				}
			}
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the lightsheet thickness: " + e + "\n. Proceeding." );
			lightsheetThickness = -1;
		}

		try
		{
			final MetadataRetrieve retrieve = (MetadataRetrieve)r.getMetadataStore();

			float cal = 0;

			PositiveFloat f = retrieve.getPixelsPhysicalSizeX( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension X seems corrupted, setting to 1." );
			}
			calX = cal;

			f = retrieve.getPixelsPhysicalSizeY( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Y seems corrupted, setting to 1." );
			}
			calY = cal;

			f = retrieve.getPixelsPhysicalSizeZ( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Z seems corrupted, setting to 1." );
			}
			calZ = cal;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "An error occured parsing the calibration: " + e + "\n. Proceeding." );
			calX = calY = calZ = 1;
		};

		return true;
	}
}
