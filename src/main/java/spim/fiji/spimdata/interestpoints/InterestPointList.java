package spim.fiji.spimdata.interestpoints;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
	File file;
	List< InterestPoint > interestPoints;
	String label;
	ViewId viewId; 
	
	public InterestPointList( final String label, final ViewId viewId, final List< InterestPoint > interestPoints, final File file, final boolean savePointList )
	{
		this.label = label;
		this.viewId = viewId;
		this.file = file;
		this.interestPoints = interestPoints;
		
		if ( savePointList )
			saveInterestPointList();
	}
	
	public List< InterestPoint > getPointList()
	{ 
		// do we need to load it?
		if ( interestPoints == null )
		{
			// ok, file does not exist
			if ( file == null )
				return null;
			
			// load list from file
			if ( !loadInterestPointList() )
				return null;
			
			return interestPoints;
		}
		else
		{
			return interestPoints;
		}
	}
	
	public File getFile() { return file; }
	public String getLabel() { return label; }
	
	public void setPointList( final List< InterestPoint > list, final boolean savePointList )
	{ 
		this.interestPoints = list;
		
		if ( savePointList )
			saveInterestPointList();
	}
	
	public void setFile( final File file ) { this.file = file; }
	public void setLabel( final String label ) { this.label = label; }
	
	public boolean saveInterestPointList()
	{
		final List< InterestPoint > list = getPointList();
		
		if ( list == null )
			return false;
		
		try
		{
			final PrintWriter out = TextFileAccess.openFileWriteEx( getFile() );
			
			out.println( "id" + "\t" + "Lx" + "\t" + "Ly" + "\t" + "Lz" + "\t" + "Wx" + "\t" + "Wy" + "\t" + "Wz" + "\t" + "Correspondences" );
			
			for ( final InterestPoint p : list )
			{
				out.print( p.getId() + "\t" );
				out.print( p.getL()[0] + "\t" + p.getL()[1] + "\t" + p.getL()[2] + "\t" );
				out.print( p.getW()[0] + "\t" + p.getW()[1] + "\t" + p.getW()[2] );

				out.println();
			}
						
			out.close();
			
			return true;
		}
		catch (IOException e)
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
			final BufferedReader in = TextFileAccess.openFileReadEx( getFile() );
						
			
			
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
