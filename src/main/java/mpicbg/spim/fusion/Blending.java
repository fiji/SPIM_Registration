package mpicbg.spim.fusion;

import java.util.ArrayList;

import mpicbg.spim.registration.ViewDataBeads;

public class Blending extends CombinedPixelWeightener<Blending>
{
	final boolean useView[];
	final int numViews;
	final double[] weights;
	final double[] minDistance;
	
	final int[][] imageSizes;
	
	protected Blending( final ArrayList<ViewDataBeads> views )
	{
		super( views );
		
		numViews = views.size();
		useView = new boolean[numViews];
		weights = new double[numViews];
		minDistance = new double[numViews];
		
		// cache image sizes
		imageSizes = new int[numViews][];		
		for ( int i = 0; i < numViews; ++i )
			imageSizes[ i ] = views.get( i ).getImageSize();
	}

	@Override
	public void updateWeights(final double[][] loc, final boolean[] useView)
	{
		// check which location are inside its respective view
		int num = 0;
		for (int view = 0; view < numViews; view++)
			if (useView[view])
				num++;
		
		// compute the linear weights
		computeLinearWeights(num, loc, useView);		
	}

	@Override
	public void updateWeights(final int[][] loc, final boolean[] useView)
	{
		// check which location are inside its respective view
		int num = 0;
		for (int view = 0; view < numViews; view++)
			if (useView[view])
				num++;
		
		// compute the linear weights
		computeLinearWeights(num, loc, useView);
	}
	
	@Override
	public double getWeight(final int view) { return weights[view]; }
	
	final private void computeLinearWeights( final int num, final int[][] loc, final boolean[] useView )
	{
		if (num <= 1)
		{
			for (int i = 0; i < useView.length; i++)
				if (useView[i])
					weights[i] = 1;
				else
					weights[i] = 0;
			return;
		}
		
		// compute the minimal distance to the border for each image
		double sumInverseWeights = 0;
		for (int i = 0; i < useView.length; i++)
		{
			if (useView[i])
			{
				minDistance[i] = 1;
				for (int dim = 0; dim < 3; dim++)
				{
					final int localImgPos = loc[i][dim];
					double value = Math.min(localImgPos, imageSizes[ i ][ dim ] - localImgPos - 1) + 1;
					
					final double imgHalf = imageSizes[ i ][ dim ]/2.0;
					final double imgHalf10 = Math.round( 0.35 * imgHalf );
					
					if ( value < imgHalf10 )
						value = (value / imgHalf10);
					else
						value = 1;

					minDistance[i] *= value;
				}

				// the distance to the image, so always +1
				// minDistance[i]++;
				
				if ( minDistance[i] < 0 )
					minDistance[i] = 0;
				else if ( minDistance[i] > 1)
					minDistance[i] = 1;
				
				weights[i] = Math.pow(minDistance[i], conf.alpha);				

				sumInverseWeights += weights[i];				
			}
		}
				
		if (sumInverseWeights == 0)
		{
			for (int i = 0; i < useView.length; i++)
				weights[i] = 0;			
		}
		else
		{
			// norm them so that the integral is 1
			for (int i = 0; i < useView.length; i++)
				if (useView[i])
					weights[i] /= sumInverseWeights;
				else
					weights[i] = 0;
		}
	}

	final private void computeLinearWeights( final int num, final double[][] loc, final boolean[] useView )
	{
		if (num <= 1)
		{
			for (int i = 0; i < useView.length; i++)
				if (useView[i])
					weights[i] = 1;
				else
					weights[i] = 0;
			return;
		}
		
		// compute the minimal distance to the border for each image
		double sumInverseWeights = 0;
		for (int i = 0; i < useView.length; i++)
		{
			if (useView[i])
			{
				minDistance[i] = 1;
				for (int dim = 0; dim < 3; dim++)
				{
					final double localImgPos = loc[i][dim];
					double value = Math.min(localImgPos, imageSizes[ i ][ dim ] - localImgPos - 1) + 1;
					
					final double imgHalf = imageSizes[ i ][ dim ]/2.0;
					final double imgHalf10 = Math.round( 0.35 * imgHalf );
					
					if ( value < imgHalf10 )
						value = (value / imgHalf10);
					else
						value = 1;

					minDistance[i] *= value;
				}

				// the distance to the image, so always +1
				// minDistance[i]++;
				
				if ( minDistance[i] < 0 )
					minDistance[i] = 0;
				else if ( minDistance[i] > 1)
					minDistance[i] = 1;
				
				weights[i] = Math.pow(minDistance[i], conf.alpha);				

				sumInverseWeights += weights[i];				
			}
		}
				
		if (sumInverseWeights == 0)
		{
			for (int i = 0; i < useView.length; i++)
				weights[i] = 0;			
		}
		else
		{
			// norm them so that the integral is 1
			for (int i = 0; i < useView.length; i++)
				if (useView[i])
					weights[i] /= sumInverseWeights;
				else
					weights[i] = 0;
		}
	}
	
	@Override
	public void close() 
	{
		//IOFunctions.println(new Date(System.currentTimeMillis()) + ": Finished Blending...");
	}
}
