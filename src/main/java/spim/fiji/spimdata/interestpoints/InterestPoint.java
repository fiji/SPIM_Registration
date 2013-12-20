package spim.fiji.spimdata.interestpoints;

import mpicbg.models.Point;

public class InterestPoint extends Point
{
	private static final long serialVersionUID = 5615112297702152070L;

	protected final int id;
	
	public InterestPoint( final int id, final float[] l )
	{
		super( l );
		
		this.id = id;
	}
	
	public int getId() { return id; }

}
