package spim.fiji.spimdata.interestpoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.TextFileAccess;

/**
 * A list of interest points for a certain label, can save and load from textfile as specified in the XML
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class InterestPointList
{
	File baseDir, file;
	List< InterestPoint > interestPoints;
	List< CorrespondingInterestPoints > correspondingInterestPoints;
	String parameters;
	
	/**
	 * Instantiates a new {@link InterestPointList}
	 * 
	 * @param baseDir - the path where the xml is
	 * @param file - relative path to the file to load/save the list from, an extension is added automatically (.ip.txt &amp;&amp; .corr.txt)
	 * for interestpoints and correspondences
	 */
	public InterestPointList( final File baseDir, final File file )
	{
		this.baseDir = baseDir;
		this.file = file;
		this.interestPoints = null;
		this.correspondingInterestPoints = null;
		this.parameters = "";
	}

	/**
	 * @return - the list of interest points, tries to load it from disk if not available
	 */
	public List< InterestPoint > getInterestPoints() { return this.interestPoints; }

	/**
	 * @return - the list of corresponding interest points, tries to load it from disk if not available
	 */
	public List< CorrespondingInterestPoints > getCorrespondingInterestPoints() { return this.correspondingInterestPoints; }

	public File getBaseDir() { return baseDir; }
	public File getFile() { return file; }
	public String getParameters() { return parameters; }
	public void setParameters( final String parameters ) { this.parameters = parameters; }
	public void setInterestPoints( final List< InterestPoint > list ) { this.interestPoints = list; }
	public void setCorrespondingInterestPoints( final List< CorrespondingInterestPoints > list ) { this.correspondingInterestPoints = list; }
	
	public void setFile( final File file ) { this.file = file; }
	public void setBaseDir( final File baseDir ) { this.baseDir = baseDir; }
	
	public String getInterestPointsExt() { return ".ip.txt"; }
	public String getCorrespondencesExt() { return ".corr.txt"; }
	
	public boolean saveInterestPoints()
	{
		final List< InterestPoint > list = getInterestPoints();
		
		if ( list == null )
			return false;
		
		try
		{
			final File dir = new File( getBaseDir(), getFile().getParent() );
			
			if ( !dir.exists() )
			{
				IOFunctions.println( "Creating directory: " + dir );
				dir.mkdirs();
			}
			
			PrintWriter out = TextFileAccess.openFileWriteEx( new File( getBaseDir(), getFile().toString() + getInterestPointsExt() ) );
			
			// header
			out.println( "id" + "\t" + "x" + "\t" + "y" + "\t" + "z" );
			
			// id && coordinates in the local image stack for each interestpoint
			for ( final InterestPoint p : list )
				out.println( p.getId() + "\t" + p.getL()[0] + "\t" + p.getL()[1] + "\t" + p.getL()[2] );
						
			out.close();
			
			return true;
		}
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.saveInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}				
	}

	public boolean saveCorrespondingInterestPoints()
	{
		final List< CorrespondingInterestPoints > list = getCorrespondingInterestPoints();
		
		if ( list == null )
			return false;
		
		try
		{
			final File dir = new File( getBaseDir(), getFile().getParent() );
			
			if ( !dir.exists() )
			{
				IOFunctions.println( "Creating directory: " + dir );
				dir.mkdirs();
			}
			
			PrintWriter out = TextFileAccess.openFileWriteEx( new File( getBaseDir(), getFile().toString() + getCorrespondencesExt() ) );
			
			// header
			out.println( "id" + "\t" + "corresponding_timepoint_id" + "\t" + "corresponding_viewsetup_id" + "\t" + "corresponding_label" + "\t" + "corresponding_id" );
			
			// id of the interestpoint from this List && for the corresponding interestpoint viewid(timepointId, viewsetupId), label, and id
			for ( final CorrespondingInterestPoints p : list )
				out.println( p.getDetectionId() + "\t" + p.getCorrespondingViewId().getTimePointId() + "\t" + p.getCorrespondingViewId().getViewSetupId() + "\t" + p.getCorrespodingLabel() + "\t" + p.getCorrespondingDetectionId() );
						
			out.close();
			
			return true;
		}
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.saveCorrespondingInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}				
	}

	public boolean loadCorrespondingInterestPoints()
	{
		try 
		{
			this.correspondingInterestPoints = new ArrayList< CorrespondingInterestPoints >();

			final BufferedReader in = TextFileAccess.openFileReadEx( new File( getBaseDir(), getFile().toString() + getCorrespondencesExt() ) );			

			// the header
			do {} while ( !in.readLine().startsWith( "id" ) );
			
			while ( in.ready() )
			{
				final String p[] = in.readLine().split( "\t" );
				
				final CorrespondingInterestPoints cip = new CorrespondingInterestPoints(
						Integer.parseInt( p[ 0 ].trim() ),
						new ViewId(
							Integer.parseInt( p[ 1 ].trim() ), // timepointId 
							Integer.parseInt( p[ 2 ].trim() ) ), // viewSetupId
						p[ 3 ], // correspondingLabel,
						Integer.parseInt( p[ 4 ].trim() ) ); //correspondingDetectionId
				
				this.correspondingInterestPoints.add( cip );
			}

			in.close();

			return true;
		}
		catch ( final IOException e )
		{
			// it is normal that this file does not exist until a registration was computed
			System.out.println( "InterestPointList.loadCorrespondingInterestPoints(): " + e );
			return false;
		}
	}

	public boolean loadInterestPoints()
	{
		try
		{
			this.interestPoints = new ArrayList< InterestPoint >();

			final BufferedReader in = TextFileAccess.openFileReadEx( new File( getBaseDir(), getFile().toString() + getInterestPointsExt() ) );

			// the header
			do {} while ( !in.readLine().startsWith( "id" ) );

			while ( in.ready() )
			{
				final String p[] = in.readLine().split( "\t" );
				
				final InterestPoint point = new InterestPoint( 
						Integer.parseInt( p[ 0 ].trim() ),
						new double[]{ 
							Double.parseDouble( p[ 1 ].trim() ),
							Double.parseDouble( p[ 2 ].trim() ),
							Double.parseDouble( p[ 3 ].trim() ) } );
				
				this.interestPoints.add( point );
			}

			in.close();
			
			return true;
		} 
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.loadInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}
}
