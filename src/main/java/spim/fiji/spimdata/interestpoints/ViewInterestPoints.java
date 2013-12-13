package spim.fiji.spimdata.interestpoints;

import java.io.File;

import mpicbg.spim.data.sequence.ViewId;

public class ViewInterestPoints extends ViewId
{
	protected File interestPointFile = null;
	
	public ViewInterestPoints( final int timepointId, final int setupId )
	{
		this( timepointId, setupId, null );
	}

	public ViewInterestPoints( final int timepointId, final int setupId, final File interestPointFile )
	{
		super( timepointId, setupId );
		
		this.interestPointFile = interestPointFile;
	}
	
	public File getBeadFile() { return interestPointFile; }
	public void setBeadFile( final File interestPointFile ) { this.interestPointFile = interestPointFile; }
}
