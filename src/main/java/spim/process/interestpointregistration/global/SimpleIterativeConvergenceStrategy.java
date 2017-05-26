package spim.process.interestpointregistration.global;

import mpicbg.models.TileConfiguration;

public class SimpleIterativeConvergenceStrategy extends IterativeConvergenceStrategy
{
	public static double minMaxError = 0.75;

	final double relativeThreshold;
	final double absoluteThreshold;

	public SimpleIterativeConvergenceStrategy(
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold )
	{
		super( maxAllowedError, maxIterations, maxPlateauwidth );

		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
	}

	public SimpleIterativeConvergenceStrategy(
			final double maxAllowedError,
			final double relativeThreshold,
			final double absoluteThreshold )
	{
		super( maxAllowedError );

		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
	}

	@Override
	public boolean isConverged( TileConfiguration tc )
	{
		double avgErr = tc.getError();
		double maxErr = tc.getMaxError();

		if ( ( ( avgErr*relativeThreshold < maxErr && maxErr > minMaxError ) || avgErr > absoluteThreshold ) )
			return false;
		else
			return true;
	}
}
