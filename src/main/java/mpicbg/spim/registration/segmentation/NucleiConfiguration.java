/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package mpicbg.spim.registration.segmentation;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel3D;
import mpicbg.pointdescriptor.model.FixedModel;
import mpicbg.pointdescriptor.model.TranslationInvariantFixedModel;

public class NucleiConfiguration 
{
	public static enum RotationAxis { XAxis, YAxis, NoRotation };
	
	public boolean readNuclei = true;
	public boolean readRegistration = false;
	public boolean registerOnly = true;
	
	//public boolean globalAffine = true;
	public boolean reComputeGlobalOptimizationAndReadRANSACCorrespondences = false;
	public AbstractAffineModel3D<?> ransacModel = new AffineModel3D();
	public AbstractAffineModel3D<?> globalModel = new AffineModel3D();
	public AbstractAffineModel3D<?> icpModel = new AffineModel3D();
	public RotationAxis rotationAxis = RotationAxis.XAxis;
	
	public int maxAngleDifference = 360;
	public int timePointIndex = 0;		
	public int timePoint = 0;
	
	public int neighbors = 3;
	public int neighborsRange = 4;
	
	public int maxEpsilon = 6;
	public float minInlierRatio = 0.1f;
	
	public float nTimesBetter = 5;
	
	// anna zebrafish
	//public float minPeakValue = 0.0025f;
	//public float sigma = 3f;
	//public boolean lookForMaxima = true;

	// michael zebrafish
	//public float minPeakValue = 0.02f;
	//public float sigma = 3f;
	//public boolean lookForMaxima = true;

	// timeseries drosophila Jan
	//public float minPeakValue = 0.0366f;
	//public float sigma = 3.597f;
	//public boolean lookForMaxima = true;

	// pedros membrane fish fixed
	//public float minPeakValue = 0.04f;
	//public float sigma = 6f;
	//public boolean lookForMaxima = false;

	// bhavana acq2
	public float minPeakValue = 0.03f;
	public float sigma = 2.153f;
	public boolean lookForMaxima = true;				
	
	public TranslationInvariantFixedModel getFixedModel( final Model ransacModel )
	{
		//System.out.println( "\nRansac model:" );
		System.out.println( ransacModel );
		
		TranslationInvariantFixedModel fixedModel;
		
		if ( ransacModel instanceof RigidModel3D )
		{
			final RigidModel3D model = (RigidModel3D)ransacModel;			
			final double[] m = model.getMatrix( null );

			fixedModel = new TranslationInvariantFixedModel( m[ 0 ], m[ 1 ], m[ 2 ],
			                                                 m[ 4 ], m[ 5 ], m[ 6 ],
			                                                 m[ 8 ], m[ 9 ], m[ 10 ] );
		}
		else
		{
			final AffineModel3D model = (AffineModel3D)ransacModel;
			final double[] m = model.getMatrix( null );

			fixedModel = new TranslationInvariantFixedModel( m[ 0 ], m[ 1 ], m[ 2 ],
			                                                 m[ 4 ], m[ 5 ], m[ 6 ],
			                                                 m[ 8 ], m[ 9 ], m[ 10 ] );
		}

		//System.out.println( "\nFixed model:" );
		//System.out.println( fixedModel );

		return fixedModel;
	}

	public FixedModel getFixedTranslationModel( final Model ransacModel )
	{
		System.out.println( ransacModel );
		
		FixedModel fixedModel;
		
		if ( ransacModel instanceof RigidModel3D )
		{
			final RigidModel3D model = (RigidModel3D)ransacModel;			
			final double[] m = model.getMatrix( null );

			fixedModel = new FixedModel( m[ 0 ], m[ 1 ], m[ 2 ], m[ 3 ],
			                             m[ 4 ], m[ 5 ], m[ 6 ], m[ 7 ],
			                             m[ 8 ], m[ 9 ], m[ 10 ], m[ 11 ] );
		}
		else
		{
			final AffineModel3D model = (AffineModel3D)ransacModel;
			final double[] m = model.getMatrix( null );

			fixedModel = new FixedModel( m[ 0 ], m[ 1 ], m[ 2 ], m[ 3 ],
			                             m[ 4 ], m[ 5 ], m[ 6 ], m[ 7 ],
			                             m[ 8 ], m[ 9 ], m[ 10 ], m[ 11 ] );
		}

		//System.out.println( "\nFixed model:" );
		//System.out.println( fixedModel );

		return fixedModel;
	}
	
}
