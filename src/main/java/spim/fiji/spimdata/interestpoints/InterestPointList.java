package spim.fiji.spimdata.interestpoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
	String parameters;
	
	/**
	 * Instantiates a new {@link InterestPointList}
	 * 
	 * @param baseDir - the path where the xml is
	 * @param file - relative path to the file to load/save the list from
	 * @param interestPoints - the list of interest points
	 * @param savePointList - if the list should be saved upon instantiation of the {@link InterestPointList} object
	 * @param parameters - which parameters have been used to identify those points
	 */
	public InterestPointList( final File baseDir, final File file, final List< InterestPoint > interestPoints, final String parameters, final boolean savePointList )
	{
		this.baseDir = baseDir;
		this.file = file;
		this.interestPoints = interestPoints;
		this.parameters = parameters;
		
		if ( savePointList )
			saveInterestPointList();
	}

	/**
	 * Instantiates a new {@link InterestPointList}
	 * 
	 * @param baseDir - the path where the xml is
	 * @param file - relative path to the file to load/save the list from
	 * @param parameters - which parameters have been used to identify those points
	 */
	public InterestPointList( final File baseDir, final File file, final String parameters )
	{
		this.baseDir = baseDir;
		this.file = file;
		this.parameters = parameters;
	}

	/**
	 * @return - the list of interest points, tries to load it from disk if not available
	 */
	public List< InterestPoint > getInterestPointList()
	{ 
		// do we need to load it?
		if ( this.interestPoints == null )
		{
			// ok, file does not exist
			if ( getFile() == null )
				return null;
			
			// load list from file
			if ( !loadInterestPointList() )
				return null;
		}

		return this.interestPoints;
	}
	
	public File getBaseDir() { return baseDir; }
	public File getFile() { return file; }
	public String getParameters() { return parameters; }
	public void setParameters( final String parameters ) { this.parameters = parameters; }
	
	public void setPointList( final List< InterestPoint > list, final boolean savePointList )
	{ 
		this.interestPoints = list;
		
		if ( savePointList )
			saveInterestPointList();
	}
	
	public void setFile( final File file ) { this.file = file; }
	public void setBaseDir( final File baseDir ) { this.baseDir = baseDir; }
	
	public boolean saveInterestPointList()
	{
		final List< InterestPoint > list = getInterestPointList();
		
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
			
			final PrintWriter out = TextFileAccess.openFileWriteEx( new File( getBaseDir(), getFile().toString() ) );
			
			out.println( "id" + "\t" + "x" + "\t" + "y" + "\t" + "z" + "\t" + "Correspondences" );
			
			for ( final InterestPoint p : list )
			{
				// id
				out.print( p.getId() + "\t" );
				
				// coordinates in the local image stack
				out.print( p.getL()[0] + "\t" + p.getL()[1] + "\t" + p.getL()[2] + "\t" );
				
				// correspondences
				
				// TODO: save correspondences
				out.println( "-" );
			}
						
			out.close();
			
			return true;
		}
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.saveInterestPointList(): " + e );
			e.printStackTrace();
			return false;
		}				
	}

	public boolean loadInterestPointList()
	{
		try 
		{
			final BufferedReader in = TextFileAccess.openFileReadEx( new File( getBaseDir(), getFile().toString() ) );			
			this.interestPoints = new ArrayList< InterestPoint >();
			
			// the header
			do {} while ( !in.readLine().startsWith( "id" ) );
			
			while ( in.ready() )
			{
				final String p[] = in.readLine().split( "\t" );
				
				final InterestPoint point = new InterestPoint( 
						Integer.parseInt( p[ 0 ].trim() ),
						new float[]{ Float.parseFloat( p[ 1 ].trim() ), Float.parseFloat( p[ 2 ].trim() ), Float.parseFloat( p[ 3 ].trim() ) } );
				
				if ( !p[ 4 ].equals( "-" ) )
				{
					//TODO: load correspondences
				}
				
				this.interestPoints.add( point );
				
			}
			
			in.close();
			
			return true;
		} 
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.loadInterestPointList(): " + e );
			e.printStackTrace();
			return false;
		}
	}
}
