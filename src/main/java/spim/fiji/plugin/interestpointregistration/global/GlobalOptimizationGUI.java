package spim.fiji.plugin.interestpointregistration.global;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;

/**
 * A certain type of global optimization, must be able to define all view pairs
 * that need to be matched and optimized individually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public abstract class GlobalOptimizationGUI
{
	final SpimData2 spimData;
	final List< ViewId > viewIdsToProcess;

	public GlobalOptimizationGUI(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	defineGroups;
	defineFixedViews;

	public abstract int getNumSets();
	public abstract List< Pair< ViewId, ViewId > > defineViewPairs( final int set );
	public abstract String getDescription();
}
