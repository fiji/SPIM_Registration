/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
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
package mpicbg.util;

import spim.vecmath.Transform3D;
import spim.vecmath.Matrix4d;
import spim.vecmath.Vector3d;

import mpicbg.imglib.util.Util;
import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.RigidModel3D;

public class TransformUtils
{
	/**
	 * Return the min and max coordinate of the transformed image in each dimension
	 * relative to the dimensions of the image it is based on. This is important
	 * for computing bounding boxes.
	 *
	 * @param dimensions - the dimensions of the image
	 * @param transform - the transformation
	 *
	 * @return - double[ numDimensions ][ 2 ], in the respective dimension d
	 * double[ d ][ 0 ] is min, double[ d ][ 1 ] is max
	 */
	public static double[][] getMinMaxDim( final int[] dimensions, final CoordinateTransform transform )
	{
		final int numDimensions = dimensions.length;

		final double[] tmp = new double[ numDimensions ];
		final double[][] minMaxDim = new double[ numDimensions ][ 2 ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			minMaxDim[ d ][ 0 ] = Double.MAX_VALUE;
			minMaxDim[ d ][ 1 ] = -Double.MAX_VALUE;
		}

		// recursively get all corner points of the image, assuming they will still be the extremum points
		// in the transformed image
		final boolean[][] positions = new boolean[ Util.pow( 2, numDimensions ) ][ numDimensions ];
		Util.setCoordinateRecursive( numDimensions - 1, numDimensions, new int[ numDimensions ], positions );

		// get the min and max location for each dimension independently
		for ( int i = 0; i < positions.length; ++i )
		{
			for ( int d = 0; d < numDimensions; ++d )
			{
				if ( positions[ i ][ d ])
					tmp[ d ] = dimensions[ d ];
				else
					tmp[ d ] = 0;
			}

			transform.applyInPlace( tmp );

			for ( int d = 0; d < numDimensions; ++d )
			{
				if ( tmp[ d ] < minMaxDim[ d ][ 0 ])
					minMaxDim[ d ][ 0 ] = tmp[ d ];

				if ( tmp[ d ] > minMaxDim[ d ][ 1 ])
					minMaxDim[ d ][ 1 ] = tmp[ d ];
			}
		}

		return minMaxDim;
	}
	
	public static Matrix4d getMatrix4d( final AffineModel3D model )
	{
		final Matrix4d matrix = new Matrix4d();

		final double[] m = new double[ 12 ];
		model.getMatrix( m );

		matrix.m00 = m[ 0 ];
		matrix.m01 = m[ 1 ];
		matrix.m02 = m[ 2 ];
		matrix.m03 = m[ 3 ];
		matrix.m10 = m[ 4 ];
		matrix.m11 = m[ 5 ];
		matrix.m12 = m[ 6 ];
		matrix.m13 = m[ 7 ];
		matrix.m20 = m[ 8 ];
		matrix.m21 = m[ 9 ];
		matrix.m22 = m[ 10 ];
		matrix.m23 = m[ 11 ];
		matrix.m30 = 0;
		matrix.m31 = 0;
		matrix.m32 = 0;
		matrix.m33 = 0;

		return matrix;
	}

	public static Matrix4d getMatrix4d( final RigidModel3D model )
	{
		final Matrix4d matrix = new Matrix4d();

		final double[] m = new double[ 12 ];
		model.getMatrix( m );

		matrix.m00 = m[ 0 ];
		matrix.m01 = m[ 1 ];
		matrix.m02 = m[ 2 ];
		matrix.m03 = m[ 3 ];
		matrix.m10 = m[ 4 ];
		matrix.m11 = m[ 5 ];
		matrix.m12 = m[ 6 ];
		matrix.m13 = m[ 7 ];
		matrix.m20 = m[ 8 ];
		matrix.m21 = m[ 9 ];
		matrix.m22 = m[ 10 ];
		matrix.m23 = m[ 11 ];
		matrix.m30 = 0;
		matrix.m31 = 0;
		matrix.m32 = 0;
		matrix.m33 = 0;

		return matrix;
	}
	
	public static Transform3D getTransform3D1( final AbstractAffineModel3D<?> model )
	{
		final Transform3D transform = new Transform3D();
		final double[] m = model.getMatrix( null );

		final double[] m2 = new double[ 16 ];
		transform.get( m2 );

		for ( int i = 0; i < m.length; ++i )
			m2[ i ] = m[ i ];

		transform.set( m2 );

		return transform;
	}
	
	public static <M extends AbstractAffineModel3D<M>> Transform3D getTransform3D( final M model )
	{
		final Transform3D transform = new Transform3D();
		final double[] m = model.getMatrix( null );

		final double[] m2 = new double[ 16 ];
		transform.get( m2 );

		for ( int i = 0; i < m.length; ++i )
			m2[ i ] = m[ i ];

		transform.set( m2 );

		return transform;
	}

	public static AffineModel3D getAffineModel3D( Transform3D transform )
	{
		final double[] m = new double[16];
		transform.get( m );

		AffineModel3D model = new AffineModel3D();
		model.set( m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11] );

		return model;
	}

	public static RigidModel3D getRigidModel3D( Transform3D transform )
	{
		final double[] m = new double[16];
		transform.get( m );

		RigidModel3D model = new RigidModel3D();
		model.set( m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11] );

		return model;
	}
	
	public static void main( String[] args )
	{
		AffineModel3D m = new AffineModel3D();
		m.set( 0.87912226f, 0.425894f, 0.21391234f, -169.3024f, -0.47269103f, 0.836501f, 0.2771809f, 42.627777f, -0.0608882f, -0.34479034f, 0.93670285f, 246.59639f );
		Transform3D t = TransformUtils.getTransform3D1( m );
		Vector3d s = new Vector3d();
		t.getScale( s );
		System.out.println( s );
	}

}
