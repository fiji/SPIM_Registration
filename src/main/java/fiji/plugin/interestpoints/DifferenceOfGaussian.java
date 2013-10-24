package fiji.plugin.interestpoints;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import fiji.spimdata.SpimDataBeads;


public class DifferenceOfGaussian extends DifferenceOf
{
	public static double defaultSigma[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];

	double[] sigma;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;	

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussian newInstance() { return new DifferenceOfGaussian(); }

	@Override
	public HashMap< ViewId, List<Point> > findInterestPoints( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList<Integer> timepointindices )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean setDefaultValues( final Channel channel, final int brightness )
	{
		final int channelId = channel.getId();
		
		this.sigma[ channelId ] = 1.8f;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;

		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.001;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.008;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.03;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.1;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues( final Channel channel )
	{
		final int channelId = channel.getId();
		
		final GenericDialog gd = new GenericDialog( "Advanced values for channel " + channel.getName() );
		
		gd.addNumericField( "Sigma", defaultSigma[ channelId ], 0 );
		gd.addNumericField( "Threshold", defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima", defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.sigma[ channelId ] = defaultSigma[ channelId ] = gd.getNextNumber();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		// TODO Auto-generated method stub		
		return false;
	}
	
	@Override
	protected void init( final int numChannels )
	{
		this.sigma = new double[ numChannels ];
		this.threshold = new double[ numChannels ];
		this.findMin = new boolean[ numChannels ];
		this.findMax = new boolean[ numChannels ];

		if ( defaultSigma == null || defaultSigma.length != numChannels )
		{
			defaultSigma = new double[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultSigma[ c ] = 1.8;
				defaultThreshold[ c ] = 0.008;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}
}
