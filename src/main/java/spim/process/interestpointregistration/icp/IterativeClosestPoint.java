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
package spim.process.interestpointregistration.icp;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.TransformationModel;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPoint extends InterestPointRegistration
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	protected TransformationModel model = null;

	protected IterativeClosestPointParameters parameters;

	public IterativeClosestPoint(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess )
	{
		super( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	protected IterativeClosestPointPairwise pairwiseMatchingInstance( final PairwiseMatch pair, final String description)
	{
		return new IterativeClosestPointPairwise( pair, model, description, parameters );
	}

	@Override
	protected TransformationModel getTransformationModel() { return model; }

	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Transformation model", TransformationModel.modelChoice, TransformationModel.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType )
	{
		model = new TransformationModel( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = IterativeClosestPointParameters.maxIterations = (int)Math.round( gd.getNextNumber() );
		
		this.parameters = new IterativeClosestPointParameters( maxDistance, maxIterations );
		
		return true;
	}

	@Override
	public IterativeClosestPoint newInstance(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess )
	{
		return new IterativeClosestPoint( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}
}
