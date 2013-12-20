package spim.fiji.spimdata.interestpoints;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;

public class ViewInterestPointCollection extends ViewId
{
	protected final HashMap< String, InterestPointList > lookup;
	
	public ViewInterestPointCollection( final int timepointId, final int setupId )
	{
		super( timepointId, setupId );
		
		this.lookup = new HashMap< String, InterestPointList >();
	}
	
	public boolean contains( final String label ) { return lookup.containsKey( label ); }
	public HashMap< String, InterestPointList > getHashMap() { return lookup; }
	public InterestPointList getInterestPoints( final String label ) { return lookup.get( label ); }
	public void addInterestPoints( final InterestPointList pointList ) { lookup.put( pointList.getLabel(), pointList ); }
}
