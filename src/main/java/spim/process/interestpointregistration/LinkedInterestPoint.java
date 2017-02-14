package spim.process.interestpointregistration;

import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class LinkedInterestPoint< P > extends InterestPoint
{
	private static final long serialVersionUID = 1L;

	final P link;

	public LinkedInterestPoint( final int id, final double[] l, final boolean useW, final P link )
	{
		super( id, l, useW );

		this.link = link;
	}

	public LinkedInterestPoint( final int id, final double[] l, final P link )
	{
		this( id, l, true, link );
	}

	public P getLinkedObject() { return link; }
	
	public String toString() { return "LinkedInterestPoint " + Util.printCoordinates( l ); }
}
