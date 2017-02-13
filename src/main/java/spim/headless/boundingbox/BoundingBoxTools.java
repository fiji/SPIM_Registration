package spim.headless.boundingbox;

import spim.fiji.spimdata.boundingbox.BoundingBox;

public class BoundingBoxTools
{
	public static void main( String[] args )
	{
		BoundingBox bb = new BoundingBox(
				new int[]{ 140, 100, 400 },
				new int[]{ 500, 600, 100 } );
	}
}
