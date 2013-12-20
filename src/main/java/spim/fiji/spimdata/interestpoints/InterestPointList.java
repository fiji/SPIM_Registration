package spim.fiji.spimdata.interestpoints;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.TextFileAccess;
import mpicbg.spim.registration.bead.Bead;
import mpicbg.spim.registration.bead.BeadIdentification;

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
	
	public boolean loadInterestPointList()
	{
		// TODO
		return false;
	}
	
	public boolean saveInterestPointList()
	{
		final List< InterestPoint > list = getPointList();
		
		if ( list == null )
			return false;
		
		try
		{
			PrintWriter out = TextFileAccess.openFileWriteEx( getFile() );
			
			out.println( "ViewSetupId=" + viewId.getViewSetupId() );
			out.println( "TimePointId=" + viewId.getTimePointId() );
			out.println( "label=" + label );

			out.println( "id" + "\t" + "Lx" + "\t" + "Ly" + "\t" + "Lz" + "\t" + "Wx" + "\t" + "Wy" + "\t" + "Wz" + "\t" + "DescriptorCorrespondences"+ "\t" + "RansacCorrespondences" );
			
			for ( final InterestPoint p : list )
			{
				out.print( p.getId() + "\t" );
				out.print( p.getL()[0] + "\t" + p.getL()[1] + "\t" + p.getL()[2] + "\t" );
				out.print( p.getW()[0] + "\t" + p.getW()[1] + "\t" + p.getW()[2] + "\t" );
				/*
				for ( final BeadIdentification descBead : bead.getDescriptorCorrespondence() )
					out.print( descBead.getBeadID() + ":" + descBead.getViewID() + ";" );

				if (bead.getDescriptorCorrespondence().size() == 0)
					out.print( "0\t" );
				else
					out.print( "\t" );
				
				for ( final BeadIdentification ransacBead : bead.getRANSACCorrespondence() )
					out.print( ransacBead.getBeadID() + ":" + ransacBead.getViewID() + ";" );

				if (bead.getRANSACCorrespondence().size() == 0)
					out.print( "0" );
				*/
				out.println();
			}
						
			out.close();
		}
		catch (IOException e)
		{
			IOFunctions.println( "InterestPointList(): " + e );
			e.printStackTrace();
			return false;
		}		
		
		return true;
	}
}
