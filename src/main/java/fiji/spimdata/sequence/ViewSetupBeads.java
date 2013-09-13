package fiji.spimdata.sequence;

import mpicbg.spim.data.sequence.ViewSetup;

/**
 * A {@link ViewSetup} extended by the option of having beads present or not.
 *  
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class ViewSetupBeads extends ViewSetup
{
	/**
	 * Defines if beads are visible in this {@link ViewSetup}.
	 */
	private final boolean hasBeads;
	
	public ViewSetupBeads( final int id, final int angle, final int illumination, final int channel, final boolean hasBeads )
	{
		super( id, angle, illumination, channel );
		
		this.hasBeads = hasBeads;
	}
	
	public ViewSetupBeads( final int id, final int angle, final int illumination, final int channel,
			final int width, final int height, final int depth, final String unit, final double pixelWidth,
			final double pixelHeight, final double pixelDepth, final boolean hasBeads )
	{
		super( id, angle, illumination, channel, width, height, depth, unit, pixelWidth, pixelHeight, pixelDepth );
		
		this.hasBeads = hasBeads;
	}
	
	public boolean hasBeads() { return hasBeads; }
}
