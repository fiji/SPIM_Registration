package task;

import spim.fiji.plugin.Interest_Point_Detection;

/**
 * Created by moon on 4/30/15.
 */
public class DetectInterestPointTask extends BaseTask
{
	protected DetectInterestPointTask( String filename )
	{
		super( filename );
	}

	public void detectWithDifferenceOfMean()
	{
		Interest_Point_Detection ipd = new Interest_Point_Detection();
		ipd.defaultProcessUsingDifferenceOfMean( xmlFileName );
	}

	public void detectWithDifferenceOfGaussian()
	{
		Interest_Point_Detection ipd = new Interest_Point_Detection();
		ipd.defaultProcessUsingDifferenceOfGaussian( xmlFileName );
	}

	public static void main(String[] argv)
	{
		// So far using DifferenceOfMean
		DetectInterestPointTask task = new DetectInterestPointTask( argv[0] );
		task.detectWithDifferenceOfMean();
	}
}
