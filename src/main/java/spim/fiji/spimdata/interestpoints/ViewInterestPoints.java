package spim.fiji.spimdata.interestpoints;

import java.io.File;

import mpicbg.spim.data.sequence.ViewId;

public class ViewInterestPoints extends ViewId
{
	protected File beadFile = null;
	
	public ViewInterestPoints( final int timepointId, final int setupId )
	{
		this( timepointId, setupId, null );
	}

	public ViewInterestPoints( final int timepointId, final int setupId, final File beadFile )
	{
		super( timepointId, setupId );
		
		this.beadFile = beadFile;
	}
	
	public File getBeadFile() { return beadFile; }
	public void setBeadFile( final File beadFile ) { this.beadFile = beadFile; }
}
